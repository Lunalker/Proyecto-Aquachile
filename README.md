#  Proyecto AquaChile

Sistema fullstack para la **gestión de evaluación de candidatos**. Permite registrar candidatos, crear solicitudes de evaluación y gestionar las evaluaciones asociadas, con un panel para dar seguimiento al estado de cada solicitud (Pendiente, En proceso, Finalizada) y al resultado de la evaluación (Recomendado / No recomendado).

##  Integrantes

| Integrante | Se encargan de |
|------------|-----|
| **Deiner Quero** | Backend |
| **Victor Huenchucheo** | Frontend |


## Funcionalidades principales

- **Registro de candidatos:** nombre, correo, teléfono, cargo al que postula, familia de cargo, origen (interno/externo), ubicación y unidad.
- **Solicitudes de evaluación:** asociadas a un candidato, con cargo, familia de cargo, fecha, profesional responsable, observaciones y estado.
- **Evaluaciones:** vinculadas a una solicitud, con fecha, resultado y estado de la evaluación.
- **Dashboard y panel del evaluador:** listado de solicitudes y gestión de evaluaciones.

##  Estructura del proyecto

```
Proyecto-Aquachile/
├── proyectoAquachile/      # Backend (Java + Spring Boot)
├── frontend-aquachile/     # Frontend (React + Vite)
└── Capturas del proyecto/  # Capturas de pantalla
```

##  Tecnologías

### Backend (`proyectoAquachile/`)
- **Java 21**
- **Spring Boot 4.1.1** (Spring Web MVC, Spring Data JPA / Hibernate)
- **MySQL 8.0** (ejecutado con Docker Compose)
- **Maven** y **Lombok**

### Frontend (`frontend-aquachile/`)
- **React 19** con **Vite 8**
- **React Router DOM 7** para la navegación
- **Axios** para las peticiones HTTP
- **Tailwind CSS 4** para los estilos
- **ESLint** para el linting

##  Cómo ejecutar el proyecto

### 1. Base de datos (MySQL en Docker)
```bash
cd proyectoAquachile
docker-compose up -d
```
Crea la base de datos `aquachile_db` en `localhost:3306` (usuario: `root`, contraseña: `root`).

### 2. Backend
```bash
cd proyectoAquachile
mvn spring-boot:run
```
La API queda disponible en `http://localhost:8080`.

### 3. Frontend
```bash
cd frontend-aquachile
npm install
npm run dev
Link: http://localhost:5173/
```

##  Endpoints principales de la API

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/solicitudes` | Listar todas las solicitudes |
| POST | `/api/solicitudes` | Crear una nueva solicitud |
| PUT | `/api/solicitudes/{id}/estado` | Actualizar el estado de una solicitud |
| CRUD | `/api/candidatos`, `/api/evaluaciones` | Gestión de candidatos y evaluaciones |

