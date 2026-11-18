# mrsad Android license scope and third-party notices

The first-party mrsad Android app, domain, data, companion bridge, and documentation in `dana-android/` are provided under the [MIT License](LICENSE). This does not apply to the unrelated Python panel above this directory, external service APIs/models, or third-party components. Refer to the relevant provider's terms for remote services, model output, and billing.

- The bundled Vazirmatn fonts retain their SIL Open Font License. The bundled Tesseract `eng` and `fas` language data retain Apache 2.0. The Android Tesseract wrapper and native components have their own notices. Texts for these components are in [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/) and packaged with the app.
- The Gradle Wrapper and its scripts are Gradle components under Apache 2.0. See their upstream notices and the headers in the scripts.
- Media created by a remote provider is not part of the app license. Google/OpenAI keys are supplied by the user and no key or cloud model weights are part of this distribution.
