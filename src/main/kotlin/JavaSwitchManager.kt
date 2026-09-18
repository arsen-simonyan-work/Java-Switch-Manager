import java.awt.Toolkit

/** Stable launcher name used by Linux desktop entries and window grouping. */
object JavaSwitchManager {
    @JvmStatic
    fun main(args: Array<String>) {
        // XToolkit derives WM_CLASS from the bottom stack frame when first initialized.
        // Initialize it here, before Compose can initialize it on a worker thread.
        Toolkit.getDefaultToolkit()
        com.home.javaswitchmanager.main()
    }
}
