package net.skripthub.docstool.modals

data class AddonData(var name: String,
                     var metadata: AddonMetadata,
                     var events: MutableList<SyntaxDataOld> = mutableListOf(),
                     var conditions: MutableList<SyntaxDataOld> = mutableListOf(),
                     var effects: MutableList<SyntaxDataOld> = mutableListOf(),
                     var expressions: MutableList<SyntaxDataOld> = mutableListOf(),
                     var types: MutableList<SyntaxDataOld> = mutableListOf(),
                     var functions: MutableList<SyntaxDataOld> = mutableListOf(),
                     var sections: MutableList<SyntaxDataOld> = mutableListOf(),
                     var structures: MutableList<SyntaxDataOld> = mutableListOf()) {
    fun sortLists() {
        events.sortBy { info -> info.name }
        conditions.sortBy { info -> info.name }
        effects.sortBy { info -> info.name }
        expressions.sortBy { info -> info.name }
        types.sortBy { info -> info.name }
        functions.sortBy { info -> info.name }
        sections.sortBy { info -> info.name }
        structures.sortBy { info -> info.name }
    }
}
