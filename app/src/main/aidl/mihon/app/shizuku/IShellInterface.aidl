package mihon.app.shizuku;

interface IShellInterface {
    void install(in AssetFileDescriptor apk) = 1;

    // KMK -->
    boolean uninstall(String packageName, int userId) = 2;
    // KMK <--

    void destroy() = 16777114;
}
