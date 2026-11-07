# AquaChile — Gestión de Evaluaciones Psicolaborales

**Proyecto de Vinculación con el Medio (VcM) · Desarrollo Full Stack II (DSY1104) · Duoc UC · 2026**

AquaChile es un **prototipo académico de aplicación web** que centraliza la gestión de evaluaciones psicolaborales del proceso de reclutamiento y selección. Permite registrar candidatos, crear y consultar solicitudes de evaluación, realizar seguimiento por estados y registrar resultados mediante una interfaz diferenciada por roles.

> **Alcance académico:** se utilizan exclusivamente datos ficticios o anonimizados. Esta aplicación no es un sistema corporativo oficial de AquaChile y no debe emplearse para tratar antecedentes personales reales.

## 1. Problemática y objetivo

La información sobre postulantes, cargos, profesionales responsables, solicitudes y resultados puede estar distribuida entre registros y herramientas independientes, lo que dificulta el seguimiento del proceso. Este MVP propone organizar dichos antecedentes en una aplicación centralizada y ofrecer un recorrido claro:

**Registro del candidato → Creación de solicitud → Seguimiento → Registro de evaluación → Actualización del estado.**

El propósito es demostrar cómo una interfaz web, una API y una base de datos pueden apoyar la trazabilidad y consulta de un proceso de evaluación psicolaboral.

## 2. Tecnologías y arquitectura

| Capa | Tecnologías |
| --- | --- |
| Frontend | React 19, Vite 8, JavaScript, React Router 7 y Axios |
| Interfaz | Tailwind CSS 4 y CSS |
| Backend | Java 21, Spring Boot 4.1.1, Spring Security y Spring Data JPA |
| Base de datos | MySQL 8 con Docker Compose |
| Pruebas de frontend | Vitest 5, React Testing Library, jest-dom, user-event y jsdom |
| Control de versiones | Git y GitHub |

**Arquitectura:** React → API REST de Spring Boot (backend monolítico) → MySQL. La autenticación emplea sesiones HTTP; las operaciones de modificación utilizan protección CSRF.

## 3. Funcionalidades implementadas

| Módulo | Descripción |
| --- | --- |
| Autenticación | Inicio y cierre de sesión, comprobación de la sesión activa y protección de rutas según el rol. |
| Candidatos | Registro, listado, consulta y edición de antecedentes; validaciones de formularios. |
| Solicitudes | Creación de solicitudes asociadas a candidatos, con cargo, familia, profesional responsable y observaciones. |
| Seguimiento | Listado y filtros por texto, estado y fecha. |
| Dashboard | Indicadores de candidatos y solicitudes por estado. |
| Evaluaciones | Registro de resultados y observaciones, con actualización del estado de la solicitud. |
| Interfaz | Diseño adaptable, navegación lateral y componentes reutilizables. |
| Pruebas | Tests de componentes, formularios, navegación y funciones de validación. |

Estados de solicitud utilizados: **Pendiente**, **En proceso** y **Finalizada**.

## 4. Roles y permisos

| Rol | Funciones principales |
| --- | --- |
| **Analista de Reclutamiento** (`analista`) | Registrar y editar candidatos; consultar dashboard y solicitudes; crear solicitudes de evaluación. |
| **Profesional Evaluador** (`evaluador`) | Consultar solicitudes, registrar evaluaciones y actualizar estados. |

Los usuarios son cuentas de demostración configuradas en Spring Security. **Las contraseñas deben proporcionarse mediante variables de entorno** y no publicarse en el repositorio. Los candidatos no acceden a la plataforma.

## 5. Organización del repositorio

```text
Proyecto-Aquachile/
├── frontend-aquachile/
│   ├── public/
│   ├── src/
│   │   ├── components/        Componentes reutilizables
│   │   ├── context/           Autenticación de React
│   │   ├── pages/             Pantallas y pruebas de páginas
│   │   ├── services/          Comunicación con la API
│   │   ├── utils/             Validaciones y sus pruebas
│   │   ├── App.jsx            Navegación y rutas
│   │   └── index.css          Estilos
│   ├── package.json
│   └── vite.config.js
├── proyectoAquachile/
│   ├── src/main/java/com/example/proyectoAquachile/
│   │   ├── Controller/        Endpoints REST
│   │   ├── Model/             Entidades JPA
│   │   ├── Repository/        Persistencia
│   │   └── Security/          Autenticación y autorización
│   ├── src/main/resources/application.properties
│   ├── docker-compose.yml
│   ├── pom.xml
│   └── mvnw.cmd
├── Capturas del proyecto/
│   └── Test/                  Evidencias de Vitest
└── README.md
```

## 6. Requisitos previos

- Git.
- **JDK 21**.
- **Node.js 24.15.0 o posterior de la línea 24** (o 22.22.2 o posterior de la línea 22), con npm.
- Docker Desktop con Docker Compose.
- Visual Studio Code o IntelliJ IDEA.

Deben estar disponibles los puertos **3306** (MySQL), **8080** (Spring Boot) y **5173** (Vite).

## 7. Instalación y ejecución

Los comandos siguientes están orientados a **Windows y PowerShell**. Para ejecutar la solución completa se necesitan MySQL, backend y frontend activos.

### 7.1 Clonar el proyecto

```powershell
git clone https://github.com/Lunalker/Proyecto-Aquachile.git
cd Proyecto-Aquachile
```

El repositorio se descarga desde su rama predeterminada, **`main`**.

### 7.2 Iniciar MySQL

Desde la raíz del repositorio:

```powershell
cd proyectoAquachile
docker compose up -d
docker compose ps
```

El contenedor local utiliza **MySQL 8**, crea la base `aquachile_db` y expone el puerto **3306**. Para detenerlo:

```powershell
docker compose down
```

> Las credenciales incluidas en la configuración Docker son solo para desarrollo local y deben sustituirse antes de cualquier despliegue real.

### 7.3 Configurar el login e iniciar Spring Boot

En la misma terminal PowerShell, dentro de `proyectoAquachile`, definir las contraseñas **locales** para las cuentas de prueba y ejecutar el backend:

```powershell
$env:AQUACHILE_ANALISTA_PASSWORD="CLAVE_LOCAL_DEL_ANALISTA"
$env:AQUACHILE_EVALUADOR_PASSWORD="CLAVE_LOCAL_DEL_EVALUADOR"
.\mvnw.cmd spring-boot:run
```

Sustituir los valores de ejemplo por contraseñas de demostración. También es posible configurarlas en **IntelliJ IDEA → Run → Edit Configurations → Environment variables**, una por variable.

**API local:** `http://localhost:8080/api`

La configuración de conexión a MySQL se encuentra en `proyectoAquachile/src/main/resources/application.properties`. El repositorio actual contiene además valores de demostración en ese archivo: **no deben utilizarse como credenciales reales ni mantenerse en una publicación de producción**. Se recomienda eliminarlos del código y cambiar las claves expuestas antes de un despliegue.

### 7.4 Iniciar React

Abrir otra terminal desde la raíz del repositorio:

```powershell
cd frontend-aquachile
npm ci
npm run dev -- --port 5173 --strictPort
```

**Aplicación web:** `http://localhost:5173`

Se utiliza el puerto 5173 porque es el origen autorizado en la configuración CORS del backend.

### 7.5 Verificar la compilación

En `frontend-aquachile`:

```powershell
npm run build
```

El resultado se genera en `dist/`. Compilar correctamente no sustituye las pruebas funcionales de la aplicación.

## 8. Recorrido de demostración

Con React, Spring Boot y MySQL ejecutándose, el flujo recomendado para la demostración es:

1. Iniciar sesión como **Analista**.
2. Registrar un candidato ficticio y verificar que aparezca en el dashboard.
3. Consultar y editar sus antecedentes.
4. Crear una solicitud asociada al candidato y registrar su responsable.
5. Revisar el listado de solicitudes y utilizar búsqueda y filtros.
6. Cerrar sesión e ingresar como **Evaluador**.
7. Registrar una evaluación ficticia y actualizar el estado de la solicitud.
8. Comprobar el cambio de estado y las restricciones de acceso entre roles.

## 9. Pruebas automatizadas con Vitest

La rama `main` contiene **23 casos de prueba definidos en 8 archivos**. Las pruebas de componentes simulan respuestas de la API y verifican el comportamiento del frontend sin requerir Spring Boot o MySQL durante su ejecución.

Desde `frontend-aquachile`:

```powershell
npm run test:run
```

Opcionalmente:

```powershell
npm run test:run -- --reporter=verbose
npm run test
```

| Archivo | Funcionalidad verificada |
| --- | --- |
| `Login.test.jsx` | Formulario, estado de carga, redirección según el rol y credenciales inválidas. |
| `Dashboard.test.jsx` | Presentación de candidatos y cálculo de indicadores. |
| `FormularioCandidato.test.jsx` | Campos, envío, errores de permisos y bloqueo durante el guardado. |
| `CandidatoDetalle.test.jsx` | Consulta, edición y manejo de candidato inexistente. |
| `NuevaSolicitud.test.jsx` | Creación de una solicitud asociada a candidato. |
| `ListadoSolicitudes.test.jsx` | Búsqueda, filtros y acciones según el rol. |
| `GestionarEvaluacion.test.jsx` | Registro, cambio de estado y errores por falta de permisos. |
| `validarCandidato.test.js` | Normalización y validación de datos de candidatos. |

**Evidencias:**

- [Ejecución conjunta de los tests](./Capturas%20del%20proyecto/Test/Todos%20los%20Test%20.png)
- [Capturas por módulo](./Capturas%20del%20proyecto/Test/)

Las pruebas automatizadas cubren principalmente el frontend con servicios simulados. La integración real entre frontend, API y base de datos se evalúa por separado mediante el recorrido manual.

## 10. Alcance académico y continuación del MVP

La **Evaluación Parcial 2** se centra en React, componentes, props, arreglos y `.map()`, estado y eventos, navegación, formularios con validación, responsive y pruebas Vitest. El repositorio también incorpora una API monolítica y persistencia local como avance hacia la etapa final.

La continuación del MVP contempla fortalecer la consulta del detalle de cada solicitud, la prevención de evaluaciones duplicadas, las pruebas integradas entre las tres capas y el despliegue demostrable mediante herramientas gratuitas. Las integraciones empresariales con Microsoft 365, Power Automate o IA no forman parte del alcance obligatorio.

## 11. Documentación del proyecto

- **Código e historial de desarrollo:** [Repositorio Proyecto-Aquachile](https://github.com/Lunalker/Proyecto-Aquachile).
- **Instrucciones de instalación y ejecución:** este README.
- **Evidencias de Vitest:** [Capturas del proyecto/Test](./Capturas%20del%20proyecto/Test/).

## 12. Equipo de desarrollo

| Integrante | Participación |
| --- | --- |
| **Víctor Huenchucheo** | Desarrollo frontend, diseño e integración de la interfaz y funcionalidades. |
| **Deiner Quero** | Desarrollo y mantenimiento de las pruebas automatizadas con Vitest. |

---

*Proyecto académico de Vinculación con el Medio — Desarrollo Full Stack II, Duoc UC (2026). La solución demuestra un flujo digital para la gestión de evaluaciones psicolaborales mediante tecnologías web y datos de prueba.*
