package net.skripthub.docstool.documentation

import ch.njol.skript.classes.Changer
import ch.njol.skript.classes.ClassInfo
import ch.njol.skript.doc.*
import ch.njol.skript.lang.ExpressionInfo
import ch.njol.skript.lang.SkriptEventInfo
import ch.njol.skript.lang.SyntaxElementInfo
import ch.njol.skript.lang.function.JavaFunction
import ch.njol.skript.registrations.Classes
import net.skripthub.docstool.modals.DocumentationEntryNode
import net.skripthub.docstool.modals.SyntaxDataOld
import net.skripthub.docstool.utils.EventValuesGetter
import net.skripthub.docstool.utils.ReflectionUtils
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.event.Cancellable
import org.skriptlang.skript.lang.entry.EntryValidator
import org.skriptlang.skript.lang.entry.SectionEntryData
import org.skriptlang.skript.lang.entry.util.TriggerEntryData
import org.skriptlang.skript.lang.structure.StructureInfo
import java.lang.reflect.Field
import java.util.function.Function

class GenerateSyntax {
	companion object Steve {

		private fun grabCodeName(classObj: Class<*>) : String? {
			val expectedClass: Class<*> = if (classObj.isArray) classObj.componentType else classObj
			val classInfo = Classes.getExactClassInfo(expectedClass) ?: Classes.getSuperClassInfo(expectedClass)
			if (classInfo == null)
				return null
			val noun = classInfo.name
			return if (classObj.isArray) noun.plural else noun.singular
		}

		fun generateSyntaxFromEvent(info: SkriptEventInfo<*>, getter: EventValuesGetter?): SyntaxDataOld? {
			if (info.description != null && info.description.contentEquals(SkriptEventInfo.NO_DOC)) {
				return null
			}
			val data = SyntaxDataOld()
			data.name = info.getName()
			data.id = info.id
			if (info.documentationID != null) {
				data.id = info.documentationID
			}
			data.description = cleanupSyntaxInputs(info.description as? Array<String>)
			data.examples = cleanupSyntaxInputs(info.examples as Array<String>?)
			data.patterns = cleanupSyntaxPattern(info.patterns)
			if (data.name != null && data.name!!.startsWith("On ")) {
				for (x in 0 until data.patterns!!.size)
					data.patterns!![x] = "[on] " + data.patterns!![x]
			}

			val sinceString = removeHTML(info.since)
			if (sinceString != null) {
				data.since = arrayOf(sinceString)
			}

			for (c in info.events)
				if (Cancellable::class.java.isAssignableFrom(c)) {
					data.cancellable = java.lang.Boolean.TRUE
				} else {
					data.cancellable = java.lang.Boolean.FALSE
					break
				}

			data.requiredPlugins = info.requiredPlugins as Array<String>?

			if (getter != null) {
				val classes = getter.getEventValues(info.events)
				if (classes == null || classes.isEmpty())
					return null
				val time = arrayOf("past event-", "event-", "future event-")
				val times = ArrayList<String>()
				for (x in classes.indices)
					(0 until classes[x].size)
						.mapNotNull { grabCodeName(classes[x][it]) }
						.mapTo(times) { time[x] + it }
				// Sort the event values alphabetically to prevent update churn
				times.sortBy { it }
				data.eventValues = times.toTypedArray()
			}

			data.entries = getEntriesFromStructureInfo(info)

			return data
		}

		fun generateSyntaxFromSyntaxElementInfo(info: SyntaxElementInfo<*>, sender: CommandSender? = null): SyntaxDataOld? {
			val syntaxInfoClass = info.getElementClass()
			if (syntaxInfoClass.isAnnotationPresent(NoDoc::class.java))
				return null

			val data = SyntaxDataOld()
			data.name = grabAnnotation(syntaxInfoClass, Name::class.java, {it.value}, syntaxInfoClass.simpleName)
			if (data.name.isNullOrBlank())
				data.name = syntaxInfoClass.simpleName

			data.id = grabAnnotation(syntaxInfoClass, DocumentationId::class.java, {it.value}, syntaxInfoClass.simpleName)
			data.description = grabAnnotation(syntaxInfoClass, Description::class.java, { cleanupSyntaxInputs(it.value) })
			data.examples = grabAnnotation(syntaxInfoClass, Examples::class.java, { cleanupSyntaxInputs(it.value) })
			data.since = grabAnnotation(syntaxInfoClass, Since::class.java, { cleanupSyntaxInputs(it.value) })
			data.requiredPlugins = grabAnnotation(syntaxInfoClass, RequiredPlugins::class.java, { cleanupSyntaxInputs(it.value) })

			data.patterns = cleanupSyntaxPattern(info.patterns)
			data.entries = getEntriesFromSkriptElementInfo(info, sender)

			return data
		}

		fun generateSyntaxFromExpression(info: ExpressionInfo<*, *>, classes: Array<Class<*>?>, sender: CommandSender?): SyntaxDataOld? {
			val data = generateSyntaxFromSyntaxElementInfo(info, sender) ?: return null
			data.returnType = Classes.toString(info.returnType)
			val array = ArrayList<String>()
			val expr = ReflectionUtils.newInstance(info.getElementClass())
			for (mode in Changer.ChangeMode.values()) {
				if (Changer.ChangerUtils.acceptsChange(expr, mode, *classes))
					array.add(mode.name.lowercase().replace('_', ' '))
			}

			data.changers = array.toTypedArray()
			return data
		}

		fun generateSyntaxFromStructureInfo(info: StructureInfo<*>): SyntaxDataOld? {
			val data = generateSyntaxFromSyntaxElementInfo(info) ?: return null
			val syntaxInfoClass = info.getElementClass()
			data.patterns = cleanupSyntaxPattern(info.patterns)
			data.name = grabAnnotation(syntaxInfoClass, Name::class.java, {it.value}, syntaxInfoClass.simpleName)
			if (data.name.isNullOrBlank())
				data.name = syntaxInfoClass.simpleName

			data.entries = getEntriesFromStructureInfo(info)

			return data
		}

		fun generateSyntaxFromClassInfo(info: ClassInfo<*>): SyntaxDataOld? {
			if (info.docName != null && info.docName.equals(ClassInfo.NO_DOC))
				return null
			val data = SyntaxDataOld()
			if (info.docName != null) {
				data.name = info.docName
			} else {
				data.name = info.codeName
			}
			data.id = info.documentationID ?: info.c.simpleName
			if (data.id.equals("Type")) {
				data.id += data.name?.replace(" ", "")
			}

			data.description = cleanupSyntaxInputs(info.description as? Array<String>)
			data.examples = cleanupSyntaxInputs(info.examples as? Array<String>)
			data.usage = cleanupSyntaxInputs(info.usage as? Array<String>)
			val sinceString = removeHTML(info.since)
			if (sinceString != null) {
				data.since = arrayOf(sinceString)
			}

			if (info.userInputPatterns != null && info.userInputPatterns!!.isNotEmpty()) {
				val size = info.userInputPatterns!!.size
				data.patterns = Array(size) { _ -> "" }
				var x = 0
				for (p in info.userInputPatterns!!) {
					data.patterns!![x++] = p!!.pattern()
						.replace("\\((.+?)\\)\\?".toRegex(), "[$1]")
						.replace("(.)\\?".toRegex(), "[$1]")
				}
			} else {
				data.patterns = Array(1) { _ -> info.codeName }
			}
			return data
		}

		fun generateSyntaxFromFunctionInfo(info: JavaFunction<*>): SyntaxDataOld? {
			val data = SyntaxDataOld()
			data.name = info.name
			data.id = "function_" + info.name
			data.description = cleanupSyntaxInputs(info.description as? Array<String>)
			data.examples = cleanupSyntaxInputs(info.examples as? Array<String>)
			val sb = StringBuilder()
			sb.append(info.name).append("(")
			if (info.parameters != null) {
				var index = 0
				for (p in info.parameters) {
					if (index++ != 0)
					//Skip the first parameter
						sb.append(", ")
					sb.append(p)
				}
			}
			sb.append(")")
			data.patterns = cleanupSyntaxPattern(arrayOf(sb.toString()), true)
			val sinceString = removeHTML(info.since)
			if (sinceString != null) {
				data.since = arrayOf(sinceString)
			}
			val infoReturnType = info.returnType
			if (infoReturnType != null) {
				data.returnType = if (infoReturnType.docName == null || infoReturnType.docName!!.isEmpty())
					infoReturnType.codeName
				else
					infoReturnType.docName
			}
			return data
		}

		private fun getEntriesFromSkriptElementInfo(info: SyntaxElementInfo<*>, sender: CommandSender?): Array<DocumentationEntryNode>? {
			// See if the class has a EntryValidator and try to pull that out to use as the source of truth.
			val elementClass = info.getElementClass() ?: return null
			val fields: Array<Field>

			try {
				fields = elementClass.declaredFields;
			} catch (ex: Exception) {
				sender?.sendMessage(
					"[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
						+ ChatColor.RESET + "] " + ChatColor.YELLOW + "Warning: Unable to access declared fields " +
						"for ${info.originClassPath} to find the SectionValidator."
				)

				// ex.printStackTrace();
				return null;
			}

			for (field in fields) {
				var entryValidator: EntryValidator? = null

				if (field.type.isAssignableFrom(EntryValidator::class.java)) {
					try {
						field.isAccessible = true
						entryValidator = field.get(null) as? EntryValidator ?: break
					} catch (ex: Exception) {
						sender?.sendMessage(
							"[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
								+ ChatColor.RESET + "] " + ChatColor.YELLOW + "Warning: Unable to find the " +
								"EntryValidator for ${info.originClassPath}"
						)

						// ex.printStackTrace();
						return null;
					}
				} else if (field.type.isAssignableFrom(EntryValidator.EntryValidatorBuilder::class.java)) {
					try {
						field.isAccessible = true
						val entryValidatorBuilder: EntryValidator.EntryValidatorBuilder =
							field.get(null) as? EntryValidator.EntryValidatorBuilder ?: break
						entryValidator = entryValidatorBuilder.build()

					} catch (ex: Exception) {
						sender?.sendMessage(
							"[" + ChatColor.DARK_AQUA + "Skript Hub Docs Tool"
								+ ChatColor.RESET + "] " + ChatColor.YELLOW + "Warning: Unable to find the " +
								"EntryValidator.EntryValidatorBuilder for ${info.originClassPath}"
						)

						// ex.printStackTrace();
						return null;
					}
				}

				if (entryValidator != null) {
					val entriesArray: MutableList<DocumentationEntryNode> = mutableListOf()
					for (entry in entryValidator.entryData) {
						entriesArray.add(
							DocumentationEntryNode(
								entry.key,
								!entry.isOptional,
								entry is SectionEntryData || entry is TriggerEntryData,
								entry.defaultValue.toString()
							)
						)
					}

					return entriesArray.toTypedArray()
				}
			}

			return null;
		}

		private fun getEntriesFromStructureInfo(info: StructureInfo<*>): Array<DocumentationEntryNode> {
			val entryValidator = info.entryValidator
			val entriesArray: MutableList<DocumentationEntryNode> = mutableListOf()

			if (entryValidator != null) {
				for (entry in entryValidator.entryData) {
					entriesArray.add(DocumentationEntryNode(entry.key, !entry.isOptional, entry is SectionEntryData, entry.defaultValue.toString()))
				}
			}

			return entriesArray.toTypedArray()
		}

		private fun cleanupSyntaxPattern(patterns: Array<String>, isFunctionPattern: Boolean = false): Array<String> {
			if (patterns.isEmpty()) {
				return patterns
			}
			for (i in patterns.indices) {
				patterns[i] = patterns[i]
					.replace("""\\([()])""".toRegex(), "$1")
					.replace("""-?\d+¦""".toRegex(), "")
					.replace("""-?\d+Â¦""".toRegex(), "")
					.replace("&lt;", "<")
					.replace("&gt;", ">")
					.replace("""%-(.+?)%""".toRegex()) { match ->
						match.value.replace("-", "")
					}
					.replace("""%~(.+?)%""".toRegex()) { match ->
						match.value.replace("~", "")
					}
					.replace("()", "")
					.replace("""@-\d""".toRegex(), "")
					.replace("""@\d""".toRegex(), "")
					.replace("""\d¦""".toRegex(), "")

				if (!isFunctionPattern) {
					patterns[i] = patterns[i]
						.replace("""(\w+):""".toRegex(), "")
						.replace("""\[:""".toRegex(), "[")
						.replace("""\(:""".toRegex(), "(")
						.replace("""\|:""".toRegex(), "|")
				}
			}
			return patterns
		}

		private fun cleanupSyntaxInput(input:String?) : String? {
			if (input.isNullOrBlank())
				return null
			return removeHTML(input)!!.replace("&lt;", "<").replace("&gt;", ">")
		}

		private fun cleanupSyntaxInputs(inputs: Array<String>?): Array<String>? {
			if (inputs.isNullOrEmpty()) {
				return null
			}
			return inputs.mapNotNull { cleanupSyntaxInput(it) }.toTypedArray()

		}


		/**
		 * Grabs information about an annotation on a class object and returns it
		 * @param from the class object to obtain information from
		 * @param annotation the annotation to grab from
		 * @param default a default value to use if there is no annotation or retrieved value is null
		 * @param R the return type output
		 */
		private fun <A : Annotation, R> grabAnnotation(from:Class<*>, annotation:Class<A>, retrieve:Function<A, R?>, default:R? = null) : R? {
			if (from.isAnnotationPresent(annotation)) {
				return retrieve.apply(from.getAnnotation(annotation)) ?: default
			}
			return default
		}

		/**
		 * Removes html tags from the provided string
		 * @return null when it's null or a blank string, otherwise a new string with no html tags
		 */
		private fun removeHTML(string: String?): String? {
			if (string.isNullOrBlank()) {
				return null
			}
			return string.replace("""<.+?>(.+?)</.+?>""".toRegex(), "$1")
		}

		/**
		 * Removes html tags from the provided string array
		 * @return null when it's null, an empty array or a blank string, otherwise a new string with no html tags
		 */
		private fun removeHTML(strings: Array<String>?): Array<String>? {
			if (strings.isNullOrEmpty()) {
				return null
			}
			return strings.mapNotNull { removeHTML(it) }.toTypedArray()
		}

	}
}
