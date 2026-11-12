# Jarvis | AuthorAi 0.11: file and agent workflow

## Delivery order

1. Reconstruct the PocketPal baseline at `4ebab327` and apply the existing patches from `main` in workflow order. Keep the newest multi-archive ZIM implementation. Bring the missing code-run and web fallback behavior into `main` through one additive patch.
2. Normalize local GGUF histories at the model boundary. Preserve the full OpenAI tool-call transcript for remote APIs; convert tool observations into alternating user/assistant messages for strict Jinja templates, both on the first prompt and after each tool round.
3. Allow Android files in every text chat, independently of image support or Pal configuration. Parse text, source code, HTML, PDF, DOC/DOCX, RTF, ODT, PPTX and XLSX on device. Send bounded previews with a document URI, then read later offsets or search inside extracted text through the document tool.
4. Keep multiple ZIM archives, search all mounted copies, and provide a search box in settings so an imported article can be verified without relying on the model's answer.
5. Search on the device through keyless web providers. When direct requests are blocked, open a private WebView for Google search and return bounded titles, snippets and URLs to the model. Distinguish live evidence from offline ZIM evidence.
6. Execute user-requested Python locally and expose generated PNG/JPEG/SVG, PDF, Word, HTML and code files as artifacts. Display HTML previews by default, run Python blocks with a button, and show execution output. Use Android's document picker to save an artifact and FileProvider to share it.
7. Keep an active task visible to Android as a foreground service while the agent works and stop the service after success, error or cancellation. Tool steps are written to the conversation as they complete.
8. Validate the reconstructed patch, parser and image smoke checks, typecheck/lint/unit tests and build an arm64 APK through the existing GitHub workflow. Test file import, strict GGUF follow-up, web search, ZIM lookup, Python artifact, save/share and background behavior on a device.

## Boundaries

- Pillow renders designed cards, charts and composited raster graphics. It cannot synthesize a photorealistic scene without an image-generation model.
- A foreground service supports work while the app is minimized. Android may still terminate a process under memory pressure; durable execution across process death requires a resumable task queue and model restoration.
- PDF text extraction does not include OCR for scanned page images. Long files are processed in bounded chunks to fit the model's actual context budget.
