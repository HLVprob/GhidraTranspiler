# GhidraTranspiler

![Platform](https://img.shields.io/badge/PLATFORM-WINDOWS-0078D6?style=for-the-badge&logo=windows&logoColor=white)
![Language](https://img.shields.io/badge/LANGUAGE-JAVA-f89820?style=for-the-badge&logo=openjdk&logoColor=white)
![Build](https://img.shields.io/badge/BUILD-GRADLE-02303A?style=for-the-badge&logo=gradle&logoColor=white)
![Plugin](https://img.shields.io/badge/TYPE-GHIDRA_PLUGIN-e74c3c?style=for-the-badge)

**GhidraTranspiler** is a high-fidelity plugin for Ghidra that converts PCode AST (Abstract Syntax Tree) into highly readable Pseudo-C++. It bridges the gap between raw reverse-engineering outputs and native development experiences, offering a fully themed, dynamic, and integrated side-by-side transpilation window.

##  Features
- **High-Fidelity C++ Emission**: Leverages Ghidra's native `PrettyPrinter` and `ClangTokenGroup` to traverse the semantic tokens directly. 
- **Smart Casting Engine**: Employs advanced regex filtering with negative lookaheads and possessive quantifiers to correctly handle complex pointer arithmetic without truncating function calls.
- **Theme-Aware Syntax Highlighting**: Uses Ghidra's native `GColor` API (`color.bg.decompiler`, `color.fg.decompiler.keyword`, etc.) to automatically adapt to your active Ghidra theme (Dark/Light).
- **Dynamic Signature Bar**: Automatically fetches and displays the currently focused function's full signature in the title area.
- **Auto-Sync Navigation**: Mirrors the Ghidra Decompiler. Whenever you click or navigate to a new function in the listing or decompiler, the transpiler updates instantly.
- **Zero-Clutter UI**: A minimalistic, non-editable text pane designed purely for reading and copying code to your IDE.

##  Installation

1. Download the latest `.zip` release from the Actions/Releases tab.
2. Open Ghidra and navigate to **File -> Install Extensions...**
3. Click the **+** (Add) icon in the top right corner and select the downloaded `.zip` file.
4. Check the box next to `GhidraTranspiler` to enable it.
5. Restart Ghidra.

*Alternatively, manually extract the `.zip` into your `Ghidra/Extensions` folder.*

##  Building from Source

This project uses Gradle and requires a valid Ghidra installation to compile.

1. Clone the repository.
2. Create a `gradle.properties` file in the root directory (this file is git-ignored).
3. Add your Ghidra installation path to the file:
   ```properties
   GHIDRA_INSTALL_DIR=C:/Path/To/Your/ghidra_12.0_PUBLIC
   ```
4. Run the build command:
   ```bash
   gradlew buildExtension
   ```
5. The compiled `.zip` artifact will be located in the `dist/` directory.

##  Usage
1. Open any binary in the Ghidra CodeBrowser.
2. The **Transpiler** panel should appear (defaulted to the right side). If you accidentally closed it, you can reopen it via `Window -> Transpiler`.
3. Simply click on any function in the Symbol Tree or navigate through the Assembly/Decompiler view. The Transpiler will automatically convert the current function to C++!
4. Click the **Copy** button in the top left to instantly copy the transpiled output to your clipboard.

##  License
This project is for educational and reverse-engineering purposes.
