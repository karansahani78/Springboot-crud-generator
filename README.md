
# 🚀 Spring Boot CRUD Generator (IntelliJ IDEA Plugin)

Spring Boot CRUD Generator is a custom **IntelliJ IDEA plugin** that automatically generates **production-ready CRUD layers** for Spring Boot applications using a JPA entity as the source.

With a single action, developers can eliminate repetitive boilerplate and instantly create a clean backend structure following best practices.

---

## ✨ Features

- One-click CRUD generation from a JPA Entity
- Generates Controller, Service, Repository, DTO, and Mapper
- Clean and consistent package structure
- Production-ready Spring Boot code
- Uses IntelliJ PSI (Program Structure Interface)
- Safe WriteCommandAction handling
- Java 17 compatible

---

## 🏗 Generated Project Structure

```

src/main/java/com/example/app
├── controller
│   └── UserController.java
├── service
│   └── UserService.java
├── repository
│   └── UserRepository.java
├── dto
│   └── UserDto.java
├── mapper
│   └── UserMapper.java
└── model
└── User.java

````

---

## ⚙️ How It Works

1. Select a **JPA Entity class** in IntelliJ IDEA  
2. Right-click and choose **Generate Spring Boot CRUD**  
3. The plugin analyzes the entity using IntelliJ PSI  
4. CRUD layers are generated under `src/main/java`

---

## 🧠 Technical Highlights

- IntelliJ Platform SDK
- PSI-based code analysis and generation
- Lifecycle-safe write actions
- Modular generator design
- Text-block based templates
- No reflection or runtime dependencies

---

## 🛠 Tech Stack

- Java 17
- Spring Boot
- IntelliJ Platform SDK
- Gradle
- PSI API

---

## ▶️ Running the Plugin Locally

```bash
./gradlew clean
./gradlew build
./gradlew runIde
````

This launches a sandboxed IntelliJ IDEA instance with the plugin installed.

---

## 🔮 Planned Enhancements

* Swagger / OpenAPI annotations
* Pagination and sorting support
* Security annotations (`@PreAuthorize`)
* Template engine support (Velocity / FreeMarker)
* Overwrite protection and preview before generation

---

## 👨‍💻 Author

**Karan Sahani**
Java Backend Developer | Spring Boot | IntelliJ Platform Plugins

📧 Email: [karansahani723@gmail.com](mailto:karansahani723@gmail.com)

---

## ⭐ Why This Project

This project demonstrates:

* Advanced Java backend skills
* IntelliJ IDEA plugin development
* PSI-based code generation
* Clean architecture enforcement
* Real-world developer tooling experience

If you find this useful, feel free to ⭐ the repository!

