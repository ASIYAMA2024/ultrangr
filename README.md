# KUGOO Dashboard — Native Android Cockpit (Nothing Phone 2a)

Complete native Kotlin + Jetpack Compose Android application for the **Kugoo Wish 01 / Tuttio Soleil 01** electric motorcycle equipped with a **FarDriver ND72450** controller and **JBD 20S BMS (`DB24SA05L24S100A`)**.

## How to Get Your Installable `.apk` in 1 Click (No Android Studio Needed)

Because this archive includes a pre-configured **GitHub Actions Cloud APK Compiler** (`.github/workflows/build-apk.yml`), you can generate the signed, installable `.apk` directly in the cloud for free:

1. Create a new repository on [GitHub](https://github.com/new) and upload/push the extracted contents of this ZIP file.
2. Click the **Actions** tab in your GitHub repository and select **Build KUGOO Dashboard APK**.
3. Once the workflow finishes (~2 minutes), scroll down to **Artifacts** and download **`KUGOO-Dashboard-Release-APK`**, which contains your signed, installable `app-release.apk` ready to install on your **Nothing Phone 2a**.

## Or Build Locally in Android Studio
1. Extract this ZIP and open the `KugooDashboard` folder in **Android Studio**.
2. Click **Build -> Build Bundle(s) / APK(s) -> Build APK(s)**.
3. Install `app/build/outputs/apk/release/app-release.apk` onto your Nothing Phone 2a.
