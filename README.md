# NAS-System

Simple DIY NAS (Network Attached Storage) for home use, written in Java (Spring Boot) and React, designed to run on a Raspberry Pi 5 with SATA drives and a custom 3D-printed case.  
This is **not a commercial product**, but a student project created as part of an engineering thesis.

## Project Structure

- **Backend** – Java 21 + Spring Boot (REST API for file and system management)
- **Frontend** – React + TypeScript (web interface)
- **Hardware** – Raspberry Pi 5 + Radxa SATA HAT + 2.5” SATA drives + 3D printed case

## Installation

### Backend (Spring Boot)

1. Clone the repository:
  ```sh
    git clone https://github.com/yourusername/nas-system.git
  ```
2.	Navigate to the backend:
  ```sh
    cd nas-system/backend
  ```
3.	Build and run:
  ```sh
    ./mvnw spring-boot:run
  ```
4.	The server should start at http://localhost:8080/home

Ensure you have Java 21+ and Maven installed. Modify application.properties if needed.

### Frontend (React – optional)

1.	Navigate to the frontend:
  ```sh
    cd ../frontend
  ```
2.	Install dependencies:
  ```sh
    npm install
  ```
3.	Run development server:
  ```sh
    npm start
  ```

## Features

•	Upload, browse, delete and download files  
•	View system info (RAM, CPU, Disk usage)  
•	Schedule and perform data backups  
•	View system logs and transfer history  
•	Simple and clean web interface  

## Usage

Once both parts are running:

•	Open the frontend in your browser (e.g. http://localhost:3000)  
•	Connect to the backend API (set in frontend config)  
•	Start uploading, browsing, or managing files via the UI  

## Hardware Setup

This project is designed to run on Raspberry Pi 5 + Radxa HAT with SATA drives.

•	Connect drives via SATA HAT  
•	Insert microSD with OS and deploy the backend  
•	(Optional) Use systemd to autostart the backend on boot  

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

You are free to:
- Use and distribute the code
- Modify and improve it
- Share changes under the same license

> Any derivative work must also remain open-source and licensed under GPLv3.  
> For full details, see the `LICENSE` file.

## Contact

For any questions, reach out to the authors:

ekl3m:
- Email: sq.programs@gmail.com 
- GitHub: [ekl3m](https://github.com/ekl3m)

A1gor:
- Email: igorfornal222@gmail.com
- GitHub: [A1gor](https://github.com/A1gor)

> Educational project made with passion and caffeine ☕.  
> If you find it useful, give it a ⭐!
