# Trailblaze 🗺️

**Trailblaze** is part of a personal portfolio. This repository hosts the Website version of the project, originally inspired by a university prototype focused on a **territorial planning and management platform**.  
It allows users to track land parcels, visualize maps interactively, and manage access according to user roles through a modern web interface.

---

## ✨ Features

- **Interactive Maps**
  - Display land parcels using **Google Maps API + GeoJSON**
  - Smooth rendering of geographic data
  - Filter and search parcels by specific attributes

- **Parcel & Worksheet Management**
  - Create, update, and monitor parcels
  - Handle worksheets for field operations
  - Track territorial changes over time

- **Role-based Access Control**
  - Admin, operator, and viewer roles
  - Secure session and profile management

---

## 🚀 Getting Started

### Prerequisites
- Java 11+
- Maven
- Google Cloud SDK (for deployment)
- Modern web browser

### Installation

1. Clone the repository:
```bash
git clone https://github.com/tiagoroque3/Trailblaze-Platform.git
cd Trailblaze-Platform
```

2. Build with Maven:
```bash
mvn clean package
```

3. Run locally:
```bash
mvn jetty:run
```

4. Deploy to Google Cloud App Engine:
```bash
gcloud app deploy
```

---

## 👥 User Roles

- **Admin**: full access to all operations  
- **Technician/Operator**: can manage parcels and worksheets  
- **Viewer**: read-only access to maps and data  

---

## 💻 Tech Stack

- **Backend:** Java (JAX-RS), Maven  
- **Cloud:** Google Cloud App Engine (Standard), Google Cloud Datastore  
- **Frontend:** HTML, CSS, JavaScript  
- **Maps:** Google Maps API with GeoJSON overlays  
- **Auth:** Role-based access  

---

## 📝 License / Notice

This repository originates from a university coursework project.
It is published for portfolio purposes, and reuse may be limited if no explicit license is specified.
