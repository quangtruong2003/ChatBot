# Gemini Chat Bot

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="App Icon">
</p>

<p align="center">
  <a href="#overview">Overview</a> •
  <a href="#key-features">Key Features</a> •
  <a href="#screenshots">Screenshots</a> •
  <a href="#technologies">Technologies</a> •
  <a href="#setup--installation">Setup</a> •
  <a href="#usage">Usage</a> •
  <a href="#roadmap">Roadmap</a> •
  <a href="#contributing">Contributing</a> •
  <a href="#license">License</a>
</p>

## Overview

Gemini Chat Bot is a sophisticated Android application that leverages Google's advanced Gemini AI models to provide intelligent, conversational interactions. Built with modern Android development practices and Jetpack Compose, this app delivers a seamless, responsive chat experience with multiple AI models, file handling capabilities, and rich text formatting.

## Key Features

- **Multiple Gemini AI Models**: Choose between Gemini Flash, Gemini Thinking, Gemini Pro, and Gemini Coding models
- **Rich Markdown Formatting**: Enhanced support for code blocks, tables, and formatting in AI responses
- **File Handling**: Upload and analyze PDF files, text documents, and images
- **Voice Interaction**: Record audio messages for AI transcription and response
- **Chat History Management**: Create, rename, and organize multiple conversation threads
- **Image Analysis**: Send images to Gemini for detailed analysis and context-aware responses
- **Dark/Light Theme Support**: Comfortable viewing experience in any lighting condition
- **Firebase Integration**: User authentication and secure data storage
- **Responsive UI**: Beautiful, modern interface with smooth animations and transitions

## Screenshots

<p align="center">
  <!-- Replace with actual screenshots from your app -->
  <img src="https://via.placeholder.com/230x500" width="230" alt="Chat Interface">
  <img src="https://via.placeholder.com/230x500" width="230" alt="AI Response">
  <img src="https://via.placeholder.com/230x500" width="230" alt="Model Selection">
</p>

## Technologies

- **Android Development**:
  - Kotlin
  - Jetpack Compose for modern UI
  - Coroutines & Flow for asynchronous operations
  - Android Architecture Components (ViewModel, LiveData)
  - Hilt for dependency injection
  
- **AI & Backend**:
  - Google Gemini AI API
  - Firebase Authentication
  - Firebase Firestore
  - Firebase Storage

- **Other Libraries**:
  - Coil for image loading
  - PDFBox for PDF processing
  - Material 3 Design components

## Setup & Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/quangtruong2003/ChatBot.git
   ```

2. **Set up Firebase**:
   - Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
   - Add an Android app to your Firebase project
   - Download the `google-services.json` file and place it in the `app` directory
   - Enable Authentication (Email/Password and Google Sign-In)
   - Set up Firestore Database and Storage

3. **Get Gemini API Key**:
   - Register for Gemini API access at [Google AI Studio](https://makersuite.google.com/app/apikey)
   - Add your API key in the appropriate configuration file

4. **Build and Run**:
   - Open the project in Android Studio
   - Sync Gradle files
   - Build and run on an emulator or physical device (Android 5.0+)

## Usage

1. **Authentication**:
   - Register a new account or sign in with existing credentials
   - Optional Google Sign-In support

2. **Chat Interface**:
   - Start a new chat from the main screen
   - Type messages in the text field or use attachment options
   - Select different AI models from the dropdown menu
   - View AI responses with rich formatting support

3. **File and Image Handling**:
   - Tap the attachment icon to upload files or images
   - Take photos directly from the camera
   - Send files for AI analysis

4. **History Management**:
   - View and manage chat history from the side drawer
   - Create new chat segments for different topics
   - Rename or delete previous conversations

## Roadmap

- [ ] Improve performance for long chat histories
- [ ] Add spelling check for input field
- [ ] Enhance PDF handling for large files
- [ ] Implement automatic Dark/Light mode switching
- [ ] Add comprehensive unit tests
- [ ] Optimize image processing and uploads
- [ ] Add more language models as they become available
- [ ] Support for more file types and formats
- [ ] Offline mode with cached responses

## Contributing

Contributions are welcome! If you'd like to contribute to this project, please follow these steps:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m 'Add some amazing feature'`
4. Push to your branch: `git push origin feature/amazing-feature`
5. Open a Pull Request

Please ensure your code follows the project's coding standards and includes appropriate tests.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

---

## Contact

Nguyễn Quang Trường - nguyentruongk530042003@gmail.com

Project Link: [https://github.com/quangtruong2003/ChatBot](https://github.com/quangtruong2003/ChatBot)
