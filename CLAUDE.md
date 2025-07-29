# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a WearOS (Android smartwatch) companion application that serves as the watch-side counterpart to the [Watch RIP MAC](https://github.com/jadon7/Watch-RIP-MAC) desktop project. The app enables previewing and managing Rive animation files (.riv) and ZIP media packages on smartwatches through both WiFi and wired transfer methods.

## Build Commands

### Basic Build Operations
```bash
# Build the project
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Build release APK  
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Clean build artifacts
./gradlew clean
```

### Development Workflow
```bash
# Run lint checks
./gradlew lint

# Run all tests
./gradlew test

# Generate lint report
./gradlew lintDebug
```

Note: After making file modifications, attempt compilation to identify and resolve errors iteratively (as per project cursor rules).

## Architecture Overview

### Core Components Architecture

The application follows a layered architecture pattern:

**Presentation Layer**: 
- `MainActivity.kt` - Main entry point with WiFi monitoring and broadcast receivers
- `DownloadScreen.kt` - Primary UI with network scanning and file management
- `MediaPreviewActivity.kt` - Media file viewer for images/videos from ZIP files
- `RivePreviewActivity.kt` - Rive animation player with data binding capabilities

**Utilities Layer**:
- `NetworkUtils.kt` - Local network scanning (port 8080) for server discovery
- `DownloadUtils.kt` - Multi-threaded chunked downloading with fallback
- `FileUtils.kt` - File type detection and ZIP extraction
- `RiveDataBindingHelper.kt` - Rive animation data binding integration

**Data Models**:
- `FileTypes.kt` - Core data classes (DownloadType, MediaFile, ServerAddress, etc.)
- `MediaType.kt` - Media classification (IMAGE, VIDEO)

### Transfer Methods Architecture

**Dual Transfer System**:
1. **WiFi Transfer**: Network scanning → HTTP download from desktop server (port 8080)
2. **Wired Transfer**: ADB file push → External storage monitoring → Broadcast triggers

**File Processing Pipeline**:
1. File detection (ZIP/Rive type validation)
2. Download/transfer completion
3. File processing (ZIP extraction or direct Rive loading)
4. Activity launching with appropriate preview mode
5. Temporary file cleanup

### Broadcast Communication System

The app uses a sophisticated broadcast system for wired transfer coordination:

- `OPEN_FILE_ACTION` - Triggers from external sources (ADB scripts)
- `ACTION_TRIGGER_WIRED_PREVIEW` - Internal trigger for preview processing  
- `ACTION_NEW_ADB_FILE` - Notifies active previews of new files
- `ACTION_CLOSE_PREVIOUS_PREVIEW` - Coordinates preview switching

### Rive Integration Architecture

The project includes comprehensive Rive animation support with data binding:

**Core Rive Components**:
- Rive Android SDK (v10.2.1) with local AAR fallback
- Custom data binding helper for ViewModel interaction
- Real-time property updates (time, date, system status)
- Fallback to traditional state machine inputs

**Data Binding Features**:
- ViewModel discovery and instance management
- Number/String/Boolean/Enum property binding
- Nested property path support
- Trigger event handling
- Auto-discovery of available properties

## Key Technical Details

### WearOS Specifics
- **Min SDK**: 30 (WearOS requirement)
- **Target SDK**: 34
- **Compile SDK**: 35
- **Required Feature**: `android.hardware.type.watch`
- **Standalone**: Does not require handheld app

### Dependencies
- **Jetpack Compose**: UI framework with WearOS Material components
- **ExoPlayer**: Media playback for extracted video files
- **Rive SDK**: Animation rendering with data binding
- **Kotlin Coroutines**: Async operations and network scanning

### File Storage Strategy
- **Downloads**: Internal app files directory (auto-cleanup)
- **External Transfer**: External files directory monitoring
- **Saved Files**: Persistent storage in `saved_rive` subdirectory
- **Temporary Files**: Automatic cleanup after processing

### Network Configuration
- **Security Config**: Custom network security configuration
- **Port Scanning**: Concurrent socket testing on port 8080
- **Download Strategy**: Multi-threaded chunked downloads with 2MB chunks
- **Timeouts**: 15s connect, 45s read timeouts

## Development Guidelines

### File Type Detection
Always use the utility functions for file type validation:
- `isRiveFile()` - Validates Rive file format via SDK
- `isZipFile()` - Checks ZIP magic bytes (PK headers)

### Error Handling Pattern
The codebase uses comprehensive error handling:
- Network errors with specific exception types
- Graceful degradation for Rive data binding failures
- Detailed logging with appropriate log levels (INFO/DEBUG/WARN/ERROR)

### Broadcast Receiver Pattern
When implementing broadcast functionality:
- Use explicit package targeting for security
- Register with appropriate context flags (RECEIVER_EXPORTED/NOT_EXPORTED)
- Always unregister in lifecycle cleanup methods

### Timing and Performance
The wired transfer system includes detailed timing instrumentation for optimization. When modifying transfer logic, preserve timing logs for performance analysis.

### Rive Data Binding Integration
When working with Rive animations:
- Initialize data binding through `RiveDataBindingHelper`
- Always implement fallback to traditional state machine inputs
- Use property discovery methods to identify available bindings
- Handle `ViewModelException` gracefully for missing properties

Refer to `RIVE_DATA_BINDING_README.md` for comprehensive Rive integration details.