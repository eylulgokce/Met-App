# MET App

## Repository Info

All the data preprocessing, ML models training, and the recording of the app can be found on the main branch. This README corresponds to the project report. For the purpose of recoring of the app, we use mocked accelerator data and mocked daily/weekly summaries. 

The repository of the android app can be found on android branch. 

## ML Models

### Data Source
- **Dataset:** We will use Heterogeneity Human Activity Recognition (**HHAR**), UCI Machine Learning Repository. It is a clean repository with phone accelerometer data which suits perfectly for our mission. 
  File used: `Phones_accelerometer.csv`  
  Columns: `Index, Arrival_Time, Creation_Time, x, y, z, User, Model, Device, gt`  
- **Size:** 13,062,475 samples

We cleaned the data and removed the samples with missing entries (`gt`). 
In this project we will only use accelerometer data to train our model and do predictions. 

### Activity → MET-Class Mapping

To convert HHAR activity labels to MET classes, we applied the following mapping based on typical energy costs.

| HHAR `gt` label | MET class    |
|---|---|
| `sit`, `stand`  | **Sedentary** |
| `walk`          | **Light**     |
| `bike`, `stairsup`, `stairsdown` | **Moderate** |


> **Note:** HHAR does not include explicit vigorous activities; the trained model therefore covers three classes (Sedentary/Light/Moderate).

---

## Preprocessing & Features

### Windowing
We will use sliding window technique to smooth the data (size 50 samples) over axes `x, y, z`

### Statistical Features (per window)
We compute 9 low-cost time-domain features:

- Means: `x_mean`, `y_mean`, `z_mean`  
- Variances: `x_var`, `y_var`, `z_var`  
- Standard deviations: `x_std`, `y_std`, `z_std`

### Subsampling for Iteration

Since we had limited time and resources, e.g. internet and processing power, we sampled the data by **0.1%** before training our models. 

All steps (download → clean → window → features → mapping) remained identical to ensure consistency.

---

## Training & Evaluation 

We split the sampled dataset 80% train data and 20% test data with following sample sizes:
Train: `X_train` (9023, 9), Test: `X_test` (2256, 9)

We are focusing on accuracy, weighted precision, recall and F1 metrics to evaluate the performance of models. We are using random seed as 42 where applicable so that the results can be reproduced. 


## Models Evaluated

We trained 4 different machine learning models to evaluate on our dataset. Since it is a classification problem, we chose the models accordingly. 

1. **Random Forest (RF)** — `n_estimators=10`, `n_jobs=-1`  

2. **Linear SVM via SGD** — `SGDClassifier(loss='hinge')``max_iter=1000` 
This model is chosen instead of LinearSVM to optimise processing power. When we set the loss as hinge, it corresponds to Linear SVM.

3. **Gradient Boosting (GB)** — `n_estimators=100`, `learning_rate=0.1`, `max_depth=3`  

4. **Neural Network (MLP)** — Keras Sequential:  
   `Dense(64, relu) → Dropout(0.2) → Dense(32, relu) → Dropout(0.2) → Dense(num_classes, softmax)`  
   We used  `adam` optimizer with 20 epocs and batch size 32.

Note that all the models are trained with configurations to optimize the resources. In an ideal environments, we should use the whole dataset to evaluate the models and run more iterations on the trained model to improve the model performance. 

---

## Results

| Model                              | Accuracy | Precision (weighted) | Recall (weighted) | F1 (weighted) |
|---|---:|---:|---:|---:|
| **Random Forest**       | **0.8741** | 0.8740 | 0.8741 | **0.8741** |
| Gradient Boosting      | 0.8688 | 0.8657 | 0.8688 | 0.8659 |
| Neural Network       | 0.8648 | **0.8650** | 0.8648 | 0.8507 |
| Linear SVM           | 0.7496 | 0.6456 | 0.7496 | 0.6776 |

**Selected model:** 
We chose Random Forests model since it is the best in overall accuracy and F1 metrics.
We exported the model weights and converted to onnx file so that we can integrate in our Android app.

---


## Third-Party Sources & Tools (Documented Use)

- **Dataset:** Heterogeneity Human Activity Recognition (HHAR), UCI ML Repository  
  https://archive.ics.uci.edu/dataset/344/heterogeneity+activity+recognition  
- **Libraries:**  
  - `pandas` (data handling)  
  - `scikit-learn` (RF, SGDClassifier, GradientBoosting, metrics, LabelEncoder)  
  - `tensorflow/keras` (MLP)  
  - `joblib` (model serialization)




# Android Application Design and Architecture

## 1. Overview

The **Met-App Android application** is implemented in Kotlin and built with the Gradle-based Android Studio environment.  
Its purpose is to provide an app predicting **Metabolic Equivalent of Task (MET) classes** based on user inputs or sensor-derived features.  

The app design follows the **Model–View–ViewModel (MVVM)** pattern to ensure clean separation of tasks. It is easy to maintain and scale if needed.

The design ensures:  
- **Continuous data collection** via accelerometer sensors.  
- **On device prediction** using a pre-trained machine learning model on the local machine.
- **Storage in the database** of predictions for daily and weekly summaries.  
- **Background service execution** to track activities even when the app is minimized.  
---

## 2. MVVM Architecture

The application follows MVVM principles, but extends them with a **background service** for continuous tracking.  

- **View (UI Layer)**  
  - Implemented in `MainActivity`.  
  - Renders the current activity class, distribution bar, daily and weekly summaries.  
  - Provides user controls (start/stop tracking, reset today).  
  - Requests runtime permissions and delegates all logic to the ViewModel.  

- **ViewModel (Logic Layer)**  
  - Bridges the UI with sensors, prediction, and persistence.  
  - Subscribes to the **accelerometer flow** and updates live state (current class, per-class durations).  
  - Queries the Room database for daily and weekly summaries.  
  - Exposes reactive `StateFlow` so the UI remains in sync.  

- **Model (Domain Layer)**  
  - **Sensing**: AccelerometerManager collects raw accelerometer data, maintains a sliding window, and extracts statistical features
  - **Inference**: MLPredictor produces `(MET class, confidence)` from features.
  - **Persistence**: Room database (entities, DAO, converters) stores per-minute activity records and contiguous sessions, and provides aggregation queries for summaries.

- **Background Tracking Service (beyond MVVM)**  
  - `ActivityTrackingService` runs as a sticky **background service**, consumes features continuously and performs predictions.
  - Keeps tracking active even when the UI is closed.  



---

## 3. Components of the Application

### 3.1 User Interface
- Provides input fields (if manual entry is needed) and displays activity classification results.  
- Contains dedicated parts on the screen for for:
  - **Real-time activity state** (e.g., “Moderate Activity”).  
  - **Daily summary** (time spent in each MET class).  
  - **Weekly summary** (time spent in each MET class in the last week).  

### 3.2 ViewModel Layer
- Manages communication between the UI and system services.  
- Starts and stops background services when required.  
- Provides daily and weekly data to the UI by querying the local database.  

### 3.3 Prediction Component
- Encapsulates machine learning inference.  
- Receives raw feature vectors (derived from accelerometer data).  
- Outputs one of the four MET classes.  
- Designed to be replaceable so that different models can be deployed in future versions.  

### 3.4 Accelerometer Integration
- Uses the phone’s **accelerometer sensor** to continuously capture movement signals.  
- Raw sensor readings are processed into short time-window features. 
- These features form the input to the prediction component.  
- Data collection runs in the background without requiring active user interaction.  

### 2.5 Database Layer
- Implements a **local database**.  
- Stores timestamped predictions continuously.  
- Supports queries grouped by:
  - **Day** — to show daily totals (e.g., minutes in Sedentary, Light, Moderate, Vigorous).  
  - **Week** — to provide weekly aggregated summaries.  

### 2.6 Background Service
- Runs a **background service** that continuously monitors the accelerometer and performs periodic predictions.  
- Ensures the app can log activity even when the user does not have the interface open.  
- Periodically writes predictions to the database in small batches to optimize performance.  


## 4. Design Considerations


- **Offline Functionality**  
  - All predictions and summaries are stored **locally** on the device.  
  - No internet connection is required; user data remains private.  

- **User Experience**  
  - Background execution ensures continuous monitoring without manual interaction.  
  - Summaries provide actionable insights and encourage user engagement.  

- **Scalability**  
  - Database schema allows easy extension (e.g., adding heart rate sensors or GPS).  
  - The modular prediction component supports new models or additional activity categories in future versions.  

---

## 5. Future Enhancements

- **Multi-sensor integration**: Use gyroscope and heart rate sensors alongside the accelerometer for richer predictions.  
- **Push notifications**: Remind users about daily activity milestones or inactivity alerts.  
- **Visualization improvements**: Improve the UI to be more user friendly. Provide charts and trends with interactive exploration of activity logs.  
- **Model improvements**: Improve the machine learning model for better predictions.

---

## 6. Limitations and Tools Used 
This project is build in limited time, while the main concern being to develop a functional tool with the features, rather than providing the best UI, best model or best user experience. The app can be improved in many different ways. 

Since the resources were quite limited in the time of developement (lack of stable internet, lack of a proper set-up, not a personal machine, time restriction), cloud based technologies are used such as Colab and Firebase Studio. Moreover again due to same limitations, I discovered what is `vibe programming` which allowed me to code in a simple and efficient way. I got helped from Gemini which is already integrated in all the tools I used to develop the android app (Colab, Android Studio). Moreover, I used LLM agents in ChatGPT, ClaudeAI and Google AIStudio to get inspirations on UI design, code snippets and the logic. I reviewed and integrated all the code snippets in my code and made sure that everything works as expected. 

