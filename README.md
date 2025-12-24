# 🚀 Spring Boot CRUD Generator (IntelliJ IDEA Plugin) — v1.0.5
[![Product Hunt](https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=29476)](https://www.producthunt.com/products/jetbrains)


**Spring Boot CRUD Generator** is a production-grade **IntelliJ IDEA plugin** that generates a complete **Spring Boot backend architecture** from a single JPA entity — directly inside the IDE.

It eliminates repetitive boilerplate and helps backend developers follow **clean architecture, best practices, and real-world patterns** with one right-click.

🔌 **JetBrains Marketplace**
👉 [https://plugins.jetbrains.com/plugin/29476-spring-boot-crud-generator](https://plugins.jetbrains.com/plugin/29476-spring-boot-crud-generator)

🎥 **Preview Video (YouTube)**
👉 [https://youtu.be/3Ifcibynsc0](https://youtu.be/3Ifcibynsc0)

---

## ✨ What’s New in v1.0.5

* 🔐 **Optional JWT Security**
* 🧑‍💼 **Role-based Authorization (USER / ADMIN / MODERATOR)**
* 🧾 **Global Exception Handling**
* 📄 **Swagger / OpenAPI Documentation**
* 📊 **Pagination & Sorting**
* 🕒 **JPA Auditing (createdAt, updatedAt, createdBy, updatedBy)**

Security is **fully optional** — generate a lightweight CRUD or a secured backend depending on your needs.

---

## ✨ Core Features

* One-click CRUD generation from a JPA Entity
* Generates:

  * Controller
  * Service
  * Repository
  * DTO
  * Mapper
* REST APIs with proper HTTP semantics
* Global exception handling with standardized error responses
* Swagger / OpenAPI documentation (Springdoc)
* Pagination & sorting support
* JPA Auditing support
* **Optional JWT authentication**
* **Optional role-based authorization**
* Clean and consistent package structure
* Production-ready Spring Boot code
* PSI-based source code generation
* Java 17 compatible

---

## 🔐 Optional Security (JWT)

When enabled, the plugin also generates:

* JWT Authentication (login & registration APIs)
* BCrypt password encryption
* Role-based access control
* Spring Security–ready structure
* Swagger-accessible public auth endpoints

Security is **not forced** — you decide when to use it.

---

## 🏗 Generated Project Structure

```text
src/main/java/com/example/app
├── config
│   ├── OpenApiConfig.java
│   ├── JpaAuditingConfig.java
│   └── SecurityConfig.java (optional)
├── entity
│   ├── BaseAuditEntity.java
│   └── YourEntity.java
├── dto
│   ├── YourEntityDto.java
│   ├── ErrorResponse.java
│   └── PageResponse.java
├── mapper
│   └── YourEntityMapper.java
├── repository
│   └── YourEntityRepository.java
├── service
│   └── YourEntityService.java
├── controller
│   └── YourEntityController.java
└── exception
    ├── ResourceNotFoundException.java
    ├── BadRequestException.java
    └── GlobalExceptionHandler.java
```

---

## ⚙️ How It Works

1. Create or open a **JPA Entity**
2. Right-click inside IntelliJ IDEA
3. Select **Generate Spring Boot CRUD**
4. The plugin analyzes the entity using **IntelliJ PSI**
5. All backend layers are generated instantly 🚀

No templates.
No reflection.
No runtime dependencies.

---

## 🧠 Technical Highlights

* IntelliJ Platform SDK
* PSI (Program Structure Interface / Java AST)
* Safe write operations using `WriteCommandAction`
* Modular generator architecture
* Text-block–based templates
* Clean separation of concerns
* Designed for **real backend projects**, not demos

---

## 🛠 Tech Stack

* Java 17+
* Spring Boot
* Spring Data JPA
* Spring Security (JWT – optional)
* Swagger / OpenAPI (Springdoc)
* IntelliJ Platform SDK
* Gradle

---

## ▶️ Running the Plugin Locally

```bash
./gradlew clean
./gradlew build
./gradlew runIde
```

This launches a **sandbox IntelliJ IDEA** instance with the plugin installed.

---

## 🔮 Planned Enhancements

* Configurable generation wizard (enable/disable features)
* Preview before code generation
* Multi-module project support
* More customization options
* Template engine support
* Even better security presets

---

## 👨‍💻 Author

**Karan Sahani**
Java Backend Developer | Spring Boot | IntelliJ Platform Plugins

📧 Email: [karansahani723@gmail.com](mailto:karansahani723@gmail.com)

---

## ⭐ Why This Project Matters

This plugin demonstrates:

* Advanced Java backend engineering
* IntelliJ IDEA plugin development
* PSI-based source code generation
* Clean architecture enforcement
* Real-world developer tooling

If you find this useful, ⭐ the repository and share feedback.
More improvements are coming 🚀
