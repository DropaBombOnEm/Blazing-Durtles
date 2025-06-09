# Blazing Durtles

Blazing Durtles is an Android app for WaniKani, forked from the existing codebase of Smouldering Durtles, the popular open-source client.

---

## Note on CSS Compatibility Warnings in Lint Reports

The generated file `build/reports/problems/problems-report.html` may show CSS compatibility warnings (such as missing `appearance: textfield;` or `appearance: none;` for `[type=search]` selectors). 
These warnings originate from the internal resources of the Android Gradle Plugin and cannot be fixed at the project level, as the CSS is not controlled by this repository. 
Any direct edits to the generated HTML or CSS will be overwritten on the next build. 
These warnings are harmless and can be safely ignored.
