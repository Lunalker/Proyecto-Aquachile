#  Proyecto AquaChile - Sistema de Gestión

##  Descripción del Proyecto
El **Proyecto AquaChile** es una plataforma web desarrollada para la administración y control de procesos operacionales acuícolas. El sistema incluye funcionalidades para la autenticación de usuarios, manejo de roles y permisos, una interfaz interactiva construida con **React** y **Vite**, comunicación con servicios Backend en **Spring Boot**, y una suite de pruebas automáticas implementada con **Vitest**.

---

##  Tecnologías Utilizadas

| Capa / Módulo | Tecnología / Herramienta |
| :--- | :--- |
| **Frontend** | React, Vite, JavaScript (ES6+), React Router DOM |
| **Cliente HTTP** | Axios |
| **Testing** | Vitest |
| **Estilos y Formato** | CSS3, ESLint |
| **Backend** | Java / Spring Boot |
| **Control de Versiones** | Git / GitHub |

---

##  Requisitos Previos

Asegúrate de tener instaladas las siguientes herramientas en tu entorno local antes de iniciar:

- **Node.js**: v18.0.0 o superior
- **npm**: v9.0.0 o superior
- **Java JDK**: 17 o superior (para el Backend Spring Boot)
- **Git**: Versión reciente

---

##  Instalación y Configuración

Sigue estos pasos para clonar y ejecutar el proyecto localmente:

### 1. Clonar el repositorio
```bash
git clone https://github.com/tu-usuario/Proyecto-Aquachile.git
cd Proyecto-Aquachile
```

### 2. Configurar e instalar dependencias del Frontend
```bash
cd frontend-aquachile
npm install
```

### 3. Ejecutar el servidor de desarrollo (Frontend)
```bash
npm run dev
```
La aplicación se abrirá por defecto en `http://localhost:5173`.

### 4. Ejecutar el Backend (Spring Boot)
Si estás ejecutando el backend localmente desde la raíz del servicio Java:
```bash
./mvnw spring-boot:run

## Ejecución con Docker

## Desde la raíz del proyecto, ejecuta:

docker compose up --build -d

## Para detener los servicios:

docker compose down
```



##  Ejecución de Pruebas (Tests)

El proyecto cuenta con pruebas unitarias y de integración desarrolladas con **Vitest**.

Para correr la suite de pruebas del cliente frontend:

```bash
# Ejecutar tests en modo observador (watch)
npm run test

# O ejecutar directamente con Vitest
npx vitest
```

---

##  Estructura del Proyecto

```text
Proyecto-Aquachile/
├── Capturas del proyecto/
│   ├── Captura de pantalla 2026-10-02 225345.png
│   ├── Captura de pantalla 2026-10-02 225445.png
│   ├── Captura de pantalla 2026-10-02 225503.png
│   ├── Captura de pantalla 2026-10-02 230025.png
│   ├── Captura de pantalla 2026-10-02 230550.png
│   ├── Captura de pantalla 2026-10-02 230743.png
│   └── Captura de pantalla 2026-10-02 230751.png
└── frontend-aquachile/
    ├── src/
    ├── index.html
    ├── eslint.config.js
    ├── package.json
    └── vite.config.js
```

---

##  Capturas de Pantalla

A continuación se muestran algunas de las pantallas e interfaces del sistema:

| Módulo / Vista | Captura |
| :--- | :--- |
| **Inicio de Sesión (Login)** | ![Login](./Capturas%20del%20proyecto/Captura%20de%20pantalla%202026-10-02%20225345.png) |
| **Panel Principal / Dashboard** | ![Dashboard](./Capturas%20del%20proyecto/Captura%20de%20pantalla%202026-10-02%20225445.png) |
| **Gestión de Roles y Usuarios** | ![Roles](./Capturas%20del%20proyecto/Captura%20de%20pantalla%202026-10-02%20225503.png) |
| **Pruebas y Validación (Vitest)** | ![Pruebas Vitest](./Capturas%20del%20proyecto/Captura%20de%20pantalla%202026-10-02%20230025.png) |

---

## 👥 Autores e Integrantes

- **Desarrollador / Victor Huenchucheo** - *Rol / Frontend*
- **Desarrollador / Deiner Quero ** - *Rol / Backend*
