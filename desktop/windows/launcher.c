/*
 * Infill.exe: runs the app's jars on the Java runtime installed next to it,
 * with the sound engine's DLL in app\. It does what the Debian package's
 * launcher script does (deb/infill), but as a Windows program, so there's no
 * console window and the Start menu and taskbar show the app's icon
 * (launcher.rc).
 *
 *   Infill.exe
 *   runtime\bin\javaw.exe      Eclipse Temurin's Java runtime
 *   app\infill_audio.dll       the sound engine
 *   app\lib\*.jar              the app
 *
 * If the JVM crashes (in a driver, for example) its report goes in the
 * app's data folder, crashes\.
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shlobj.h>
#include <wchar.h>

static void fail(const wchar_t *what) {
    MessageBoxW(NULL, what, L"Infill", MB_OK | MB_ICONERROR);
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR args, int show) {
    (void)instance; (void)previous; (void)show;
    wchar_t dir[MAX_PATH];
    DWORD n = GetModuleFileNameW(NULL, dir, MAX_PATH);
    if (n == 0 || n >= MAX_PATH) { fail(L"Could not find where Infill is installed."); return 1; }
    wchar_t *slash = wcsrchr(dir, L'\\');
    if (slash != NULL) *slash = 0;

    // Java reads its command line in the ANSI code page, so everything but the crash log is
    // relative to the install folder, and the crash log is only used if its path is plain ASCII.
    if (!SetCurrentDirectoryW(dir)) { fail(L"Could not open the Infill folder."); return 1; }
    wchar_t crashes[MAX_PATH] = L"";
    wchar_t appdata[MAX_PATH] = L"";
    DWORD got = GetEnvironmentVariableW(L"APPDATA", appdata, MAX_PATH);
    if (got > 0 && got < MAX_PATH) {
        _snwprintf(crashes, MAX_PATH, L"%ls\\Infill\\crashes", appdata);
        crashes[MAX_PATH - 1] = 0;
        for (const wchar_t *c = crashes; *c; c++) if (*c > 126) { crashes[0] = 0; break; }
        if (crashes[0]) SHCreateDirectoryExW(NULL, crashes, NULL);
    }

    static wchar_t command[32768];
    int length = _snwprintf(command, 32768,
        L"\"runtime\\bin\\javaw.exe\" %ls%ls%ls "
        L"\"-Djava.library.path=app\" --enable-native-access=ALL-UNNAMED "
        L"-cp \"app\\lib\\*\" com.rm.infill.desktop.MainKt %ls",
        crashes[0] ? L"\"-XX:ErrorFile=" : L"", crashes, crashes[0] ? L"\\hs_err_%p.log\"" : L"",
        args ? args : L"");
    if (length < 0) { fail(L"The command line is too long."); return 1; }

    STARTUPINFOW startup = {0};
    startup.cb = sizeof startup;
    PROCESS_INFORMATION process = {0};
    if (!CreateProcessW(NULL, command, NULL, NULL, FALSE, 0, NULL, dir, &startup, &process)) {
        fail(L"Could not start Java. The runtime folder beside Infill.exe may be missing: try installing again.");
        return 1;
    }
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return 0;
}
