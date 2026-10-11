# Privacy policy: NavCert Offline

Last updated: 12 October 2026
Developer: Aadidev Raizada (N152), NMIMS MPSTME Mumbai

NavCert Offline scans maritime certificates and other documents on your phone. It is built so that your documents never leave the device.

## What the app collects

Nothing. The app has no account, no server, no analytics, no advertising and no crash reporting. The developer receives no data from the app.

## No network access

The app's manifest removes the `INTERNET` and `ACCESS_NETWORK_STATE` permissions, including the ones requested by bundled libraries (`tools:node="remove"`). Without the `INTERNET` permission, Android blocks every network connection from the app.

## All processing on the device

Every step runs on the phone, with models bundled inside the app:

- page detection, perspective correction and enhancement (OpenCV)
- printed text recognition (ML Kit Text Recognition, bundled model)
- handwriting recognition (PP-OCRv5 on ONNX Runtime)
- certificate field extraction (rules, a fine-tuned MiniLM on ONNX Runtime, MobileBERT on TensorFlow Lite)
- answers to questions about saved documents (local retrieval and MobileBERT)

## Permissions

| Permission | Used | Why |
| --- | --- | --- |
| Internet, network state | No (removed) | The app works offline by design. |
| Camera | No | Photos are taken by the system camera app into a private, temporary file the app shares through a `FileProvider`. |
| Storage, photos and media | No | Images are chosen with the system picker, which grants access to the selected image only. |
| Location | No | Not needed. |

## Where your documents are stored

Saved documents (text, notes, highlights) and preview images are stored in the app's private storage (`files/documents/`). Other apps cannot read this folder. Camera captures and crops are kept in the app's cache.

Backup is disabled (`android:allowBackup="false"`), and the backup and data-extraction rules exclude all app data from cloud backup and device-to-device transfer.

## Sharing

Text leaves the app only when you choose to share it (Share button, through the Android share sheet) or copy it to the clipboard. The app never sends anything on its own.

## Deleting your data

- Delete a single document from the Documents screen.
- Clear all data from Android Settings > Apps > NavCert Offline > Storage > Clear storage.
- Uninstalling the app removes all documents, previews and model files.

## Children

The app is not directed at children and collects no personal information from anyone.

## Data safety summary (Google Play)

- Data collected: none.
- Data shared with third parties: none.
- Data encrypted in transit: not applicable (no data is transmitted).
- Users can request deletion: yes, all data is local and deletable in the app or through Android settings.

## Contact

Questions about this policy: open an issue at github.com/AadidevRaizada/navcert-offline.
