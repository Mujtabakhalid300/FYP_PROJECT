# FYP: Android Application for Running VLMs Locally Using MNN

## Description
This Android application is currently under development and aims to run **Vision-Language Models (VLMs)** locally using the **MNN (Mobile Neural Network)** library. The app is designed to leverage the power of on-device AI for tasks such as image recognition, natural language processing, and more.

## Features
- **On-Device AI**: Runs VLMs locally without requiring cloud connectivity.
- **MNN Integration**: Utilizes the MNN library for efficient neural network inference.
- **Custom Model Support**: Currently supports the **Qwen2-VL-2B-Instruct-MNN** model.
- **High Performance**: Optimized for mid-to-high-spec Android devices.

## Installation

### Prerequisites
- Android Studio (latest version recommended)
- Git
- A mid-to-high-spec Android device (emulator is not supported)

### Steps
1. **Clone and Build MNN Library**:
   - Clone the MNN repository:
     ```bash
     git clone https://github.com/alibaba/MNN.git
     ```
   - Build the MNN library. Refer to the [MNN documentation](https://www.yuque.com/mnn/en) for detailed instructions.
   - Note the path to the MNN parent directory (you'll need it in Step 3).

2. **Open the Project**:
   - Open this project in Android Studio and let it configure itself.

3. **Update CMakeLists.txt**:
   - Open the `/main/cpp/CMakeLists.txt` file.
   - Update the `MNN_ROOT` variable to point to the MNN path from Step 1.

4. **Download and Add the Model**:
   - Download the **Qwen2-VL-2B-Instruct-MNN** model (currently the only supported model).
   - Move the model files to the `assets/models/` directory in the project.

5. **Set File Encoding and EOL**:
   - Ensure all model files are encoded in **UTF-8**.
   - Set the End-of-Line (EOL) format to **LF (Unix)** for all model files.

6. **Run the Project**:
   - Connect your Android device to your computer.
   - Run the project from Android Studio.

## Usage
Once the app is installed and running on your device:
1. Open the app.
2. Follow the on-screen instructions to interact with the VLM.
3. Test the app with various inputs to see the model's performance.

## Configuration
- **Model Path**: Ensure the model files are correctly placed in the `assets/models/` directory.
- **MNN Path**: Verify that the `MNN_ROOT` variable in `CMakeLists.txt` points to the correct MNN directory.

## Contributing
Contributions are welcome! If you'd like to contribute to this project, please follow these steps:
1. Fork the repository.
2. Create a new branch for your feature or bugfix.
3. Commit your changes and push to your branch.
4. Submit a pull request with a detailed description of your changes.

## License
This project is licensed under the [MIT License](LICENSE).

## Acknowledgments
- Thanks to the **MNN** team for providing an efficient neural network inference library.
- Special thanks to the developers of the **Qwen2-VL-2B-Instruct-MNN** model for making it compatible with MNN.

---

**Note**: This project is currently in development and may have limitations. Testing is recommended on mid-to-high-spec Android devices.
