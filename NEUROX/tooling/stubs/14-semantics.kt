// NEUROX offline compile stubs - semantics properties used by the app.
package androidx.compose.ui.semantics

interface SemanticsPropertyReceiver

var SemanticsPropertyReceiver.contentDescription: String
    get() = ""
    set(_) {}

var SemanticsPropertyReceiver.stateDescription: String
    get() = ""
    set(_) {}

var SemanticsPropertyReceiver.heading: Unit
    get() = Unit
    set(_) {}

var SemanticsPropertyReceiver.role: Role
    get() = Role.Button
    set(_) {}

class Role private constructor() {
    companion object {
        val Button = Role()
        val Checkbox = Role()
        val Switch = Role()
        val RadioButton = Role()
        val Tab = Role()
        val Image = Role()
        val DropdownList = Role()
    }
}
