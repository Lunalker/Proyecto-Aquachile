# AquaChile — Sistema de Gestión de Evaluaciones Psicolaborales

**Proyecto académico de Vinculación con el Medio (VcM) | Desarrollo Full Stack II (DSY1104) — Duoc UC**

Aplicación web tipo **MVP (Producto Mínimo Viable)** para centralizar el registro de candidatos, la creación y seguimiento de solicitudes de evaluación psicolaboral y el registro de resultados del proceso de reclutamiento y selección.

> **Alcance:** prototipo académico elaborado con datos ficticios o anonimizados. No corresponde a un sistema corporativo oficial de AquaChile ni está preparado para almacenar datos personales reales en producción.

## 1. Problema y objetivo

El proceso de evaluaciones psicolaborales involucra candidatos, cargos, profesionales responsables, solicitudes, estados y resultados. Mantener estos datos en herramientas y registros separados dificulta su seguimiento.

El objetivo del proyecto es **centralizar la información en una sola aplicación**, facilitar la trazabilidad de cada solicitud y mostrar indicadores que apoyen la gestión del proceso. El recorrido principal es:

**Registro de candidato → Creación de solicitud → Seguimiento → Registro de evaluación → Actualización del estado.**

## 2. Tecnologías

| Capa | Tecnologías |
| --- | --- |
| Frontend | React 19, Vite 8, JavaScript, React Router 7, Axios y Tailwind CSS 4 |
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC, Spring Data JPA y Spring Security |
| Base de datos | MySQL 8, ejecutado con Docker Compose |
| Pruebas frontend | Vitest 5, React Testing Library, jest-dom, user-event y jsdom |
| Control de versiones | Git y GitHub |

La solución sigue una **arquitectura monolítica** en el backend: React se comunica mediante HTTP con una API REST de Spring Boot, que utiliza JPA para acceder a MySQL.

## 3. Funcionalidades del MVP

| Módulo | Funcionalidades implementadas |
| --- | --- |
| Autenticación | Inicio y cierre de sesión, comprobación de sesión y acceso condicionado por rol. |
| Candidatos | Registro, listado, consulta y edición de los antecedentes principales. |
| Solicitudes | Creación de solicitudes vinculadas a un candidato; cargo, familia, responsable y observaciones. |
| Seguimiento | Listado de solicitudes, búsqueda por texto y filtros por estado y fecha. |
| Dashboard | Indicadores de candidatos y solicitudes por estado. |
| Evaluaciones | Registro de resultado/observaciones y actualización del estado de una solicitud. |
| Interfaz | Navegación con React Router, componentes reutilizables y diseño adaptable a distintas pantallas. |
| Calidad | Pruebas automatizadas de componentes, formularios, navegación y lógica de validación. |

Las operaciones de interfaz se apoyan en la API existente; para demostrar el flujo completo de esta implementación es necesario iniciar el backend y la base de datos.

## 4. Roles y permisos

| Rol | Permisos principales |
| --- | --- |
| **Analista de Reclutamiento** (`analista`) | Consultar dashboard, registrar y editar candidatos, crear solicitudes y consultar su seguimiento. |
| **Profesional Evaluador** (`evaluador`) | Consultar solicitudes, registrar evaluaciones y actualizar sus estados. |

Las cuentas son **usuarios de demostración en memoria** administrados por Spring Security. Los candidatos no acceden directamente a la plataforma. Las contraseñas se deben configurar en cada equipo mediante variables de entorno; **no deben publicarse en el repositorio**.

## 5. Estructura del repositorio

```text
Proyecto-Aquachile/
├── frontend-aquachile/
│   ├── public/                 # Recursos públicos
│   ├── src/
│   │   ├── components/         # Componentes reutilizables
│   │   ├── context/            # Estado de autenticación
│   │   ├── pages/              # Pantallas y tests de páginas
│   │   ├── services/           # Cliente HTTP / API
│   │   ├── utils/              # Validaciones y sus pruebas (versión integrada)
│   │   ├── App.jsx             # Rutas y navegación
│   │   └── index.css           # Estilos generales
│   ├── package.json
│   └── vite.config.js
├── proyectoAquachile/
│   ├── src/main/java/com/example/proyectoAquachile/
│   │   ├── Controller/         # Endpoints REST
│   │   ├── Model/              # Entidades JPA
│   │   ├── Repository/         # Acceso a datos
│   │   └── Security/           # Autenticación y permisos
│   ├── src/main/resources/application.properties
│   ├── docker-compose.yml
│   ├── mvnw / mvnw.cmd
│   └── pom.xml
├── Capturas del proyecto/
│   └── Test/                   # Evidencias de Vitest subidas al repositorio
└── README.md
```

## 6. Requisitos previos

- **Git** instalado.
- **Node.js 24.15.0 o superior de la línea 24**, recomendado para las dependencias actuales; alternativamente, Node.js 22.22.2 o superior de la línea 22. Incluye npm.
- **JDK 21** (`java -version`).
- **Docker Desktop** con Docker Compose disponible.
- Editor recomendado: **VS Code** para React e **IntelliJ IDEA** para Spring Boot (ambos proyectos pueden abrirse en VS Code).

Los puertos **3306**, **8080** y **5173** deben estar disponibles.

## 7. Descargar el proyecto

En una terminal:

```bash
git clone -b Feat--Avance-ViTest https://github.com/Lunalker/Proyecto-Aquachile.git
cd Proyecto-Aquachile
```

**Importante:** el comando descarga los cambios que estén publicados en esa rama. Antes de entregar el proyecto, el equipo debe subir también los cambios de la **interfaz profesional integrada**, las validaciones y las correcciones de tests para que otros puedan reproducir la misma versión.

## 8. Iniciar la base de datos (MySQL)

Abrir una terminal en la carpeta del backend:

```powershell
cd proyectoAquachile
docker compose up -d
```

El archivo `docker-compose.yml` crea el contenedor `aquachile-mysql`, utiliza **MySQL 8** y prepara la base de datos `aquachile_db` en el puerto `3306`.

Para comprobar su estado:

```powershell
docker compose ps
```

Para detener el contenedor:

```powershell
docker compose down
```

> Las credenciales de MySQL del `docker-compose.yml` corresponden exclusivamente a un entorno local de desarrollo. Deben sustituirse y gestionarse de forma segura antes de cualquier despliegue público.

## 9. Configurar e iniciar el backend

### Variables de entorno para el login

El backend espera estas dos variables:

- `AQUACHILE_ANALISTA_PASSWORD`: contraseña local de la cuenta `analista`.
- `AQUACHILE_EVALUADOR_PASSWORD`: contraseña local de la cuenta `evaluador`.

**Opción A — PowerShell (Windows):** desde la carpeta `proyectoAquachile`, ejecutar en la **misma terminal** que se usará para iniciar Spring Boot:

```powershell
$env:AQUACHILE_ANALISTA_PASSWORD="TU_CLAVE_LOCAL_ANALISTA"
$env:AQUACHILE_EVALUADOR_PASSWORD="TU_CLAVE_LOCAL_EVALUADOR"
.\mvnw.cmd spring-boot:run
```

Sustituir los textos `TU_CLAVE_LOCAL_...` por las contraseñas elegidas para la demostración. No subir esas claves a GitHub. Las variables asignadas de este modo duran únicamente mientras se mantiene abierta la terminal de PowerShell.

**Opción B — IntelliJ IDEA:** abrir **Run → Edit Configurations → ProyectoAquachileApplication → Environment variables** y agregar ambas variables (una por fila). Guardar la configuración y ejecutar la aplicación.

La API quedará disponible en:

```text
http://localhost:8080/api
```

El proyecto utiliza `application.properties` para la conexión local a MySQL. Si el puerto o las credenciales de la base de datos cambian, actualizar esta configuración de manera segura.

**Seguridad:** antes de publicar la versión final, eliminar cualquier valor de contraseña de demostración que aún exista en `application.properties`, rotar las claves expuestas y conservar solo la configuración por entorno.

## 10. Instalar e iniciar el frontend

En **otra terminal**, desde la raíz del repositorio:

```powershell
cd frontend-aquachile
npm ci
npm run dev
```

Abrir en el navegador:

```text
http://localhost:5173
```

La configuración del cliente HTTP apunta a `http://localhost:8080/api`, utiliza cookies de sesión y envía el token CSRF en las operaciones que modifican información. El backend autoriza el origen `http://localhost:5173`; si Vite selecciona otro puerto, debe ajustarse la configuración para mantener la comunicación.

### Comprobar compilación de producción

```powershell
npm run build
```

El comando genera los archivos estáticos en `frontend-aquachile/dist/` y permite comprobar que el frontend compila. **Una compilación exitosa no reemplaza las pruebas funcionales ni la verificación del backend.**

## 11. Probar el sistema manualmente

Usar únicamente datos ficticios. Recorrido recomendado:

1. Iniciar sesión como **Analista** y comprobar que se muestre el dashboard.
2. Registrar un candidato ficticio y verificar que aparezca en el listado.
3. Abrir su ficha, editar un dato y guardar los cambios.
4. Crear una solicitud asociada al candidato, asignando cargo y profesional responsable.
5. Consultar la lista de solicitudes y probar las búsquedas y filtros.
6. Cerrar sesión e ingresar como **Evaluador**.
7. Abrir una solicitud, registrar una evaluación ficticia y cambiar su estado.
8. Volver al listado y verificar que se refleje la actualización.
9. Comprobar que cada rol solo vea las acciones autorizadas.
10. Repetir la revisión en resoluciones de escritorio y celular.

Este recorrido requiere **React, Spring Boot y MySQL** funcionando. La ejecución local completa debe verificarse en el equipo que realizará la demostración.

## 12. Pruebas automatizadas con Vitest

Las pruebas están organizadas por pantalla y por lógica de validación. Se utiliza React Testing Library para interactuar con los componentes y **mocks** de la API para aislarlos de Spring Boot y MySQL.

Desde `frontend-aquachile`:

```powershell
npm run test:run
```

Para ejecutar Vitest en modo observación:

```powershell
npm run test
```

Para obtener más detalle de cada caso:

```powershell
npm run test:run -- --reporter=verbose
```

### Cobertura de pruebas

El proyecto define `npm run test:coverage`, pero este comando necesita tener instalada una versión compatible de `@vitest/coverage-v8`. Instalarla y registrar el resultado **solo si el equipo decide presentar cobertura**; la rúbrica no exige un porcentaje específico.

### Casos cubiertos

| Archivo | Comportamientos verificados |
| --- | --- |
| `Login.test.jsx` | Formulario, estado de carga, acceso por rol y credenciales incorrectas. |
| `Dashboard.test.jsx` | Carga de candidatos e indicadores de solicitudes. |
| `FormularioCandidato.test.jsx` | Campos, guardado, errores de permisos y bloqueo durante el envío. |
| `CandidatoDetalle.test.jsx` | Consulta, edición y candidato inexistente. |
| `NuevaSolicitud.test.jsx` | Registro de solicitud vinculada a un candidato. |
| `ListadoSolicitudes.test.jsx` | Listado, búsqueda, filtro por estado y acción del Evaluador. |
| `GestionarEvaluacion.test.jsx` | Registro de evaluación, actualización del estado y acceso denegado. |
| `validarCandidato.test.js` | Reglas de validación de candidatos (versión integrada). |

Las **capturas incorporadas al repositorio** se encuentran en [Capturas del proyecto/Test](./Capturas%20del%20proyecto/Test/). Para la entrega, guardar además una evidencia reciente de la ejecución de `npm run test:run` después de integrar los cambios, indicando casos, resultados y una breve justificación de los escenarios probados.

> Estas pruebas verifican principalmente lógica y comportamiento del frontend con servicios simulados. No sustituyen una prueba de integración real contra Spring Boot y MySQL.

## 13. Estado actual y trabajo pendiente

**Implementado en el código del MVP:** componentes y rutas React, formularios, búsqueda y filtros, dashboard, autenticación por roles, endpoints REST, entidades JPA, configuración de MySQL y pruebas Vitest. La versión local integrada incorpora además la interfaz profesional y validaciones reutilizables.

**Pendiente de verificar o completar para el MVP final:**

- Comprobar y documentar el flujo completo con frontend, backend y MySQL ejecutándose juntos.
- Incorporar una **vista específica de detalle de solicitud** con todos sus antecedentes, de acuerdo con el anexo de requerimientos.
- Mejorar la gestión de evaluaciones para evitar registros duplicados y operaciones parcialmente guardadas.
- Verificar la consulta y registro de la **fecha de evaluación**; actualmente la entidad del backend la establece automáticamente al crear el registro.
- Verificar el diseño responsive en distintos dispositivos y corregir problemas de usabilidad.
- Completar la documentación de integración, las evidencias y el despliegue gratuito demostrable para la etapa final.

El proyecto no incluye integraciones corporativas con Microsoft 365, Power Automate, Copilot ni automatizaciones mediante inteligencia artificial, porque están fuera del alcance obligatorio del MVP académico.

## 14. Documentos y evidencias de entrega

Para la **Evaluación Parcial 2**, se consideran los siguientes entregables:

| Entregable | Ubicación o acción |
| --- | --- |
| Código fuente e historial del equipo | Repositorio GitHub actualizado, en la rama de entrega acordada. |
| Instrucciones de ejecución | Este `README.md` en la raíz del repositorio. |
| ERS actualizado | Documento **ERS AquaChile — Evaluación Parcial 2**, entregado por separado en AVA; adjuntar también al repositorio si el equipo lo acuerda. |
| Evidencia Vitest | Capturas y descripción breve de los casos, ejecución, resultados y justificación. |
| Demostración | Ejecución del flujo y explicación individual del código y de las pruebas. |

La evaluación parcial se centra en el **frontend y las pruebas unitarias**. La integración efectiva con backend y base de datos es parte de la etapa posterior del MVP, aunque el equipo ya tiene implementados esos componentes.

## 15. Equipo de desarrollo

| Integrante | Participación |
| --- | --- |
| Víctor Huenchucheo | Desarrollo frontend e integración de la interfaz *(confirmar distribución definitiva)*. |
| Deiner Quero | Desarrollo backend *(confirmar distribución definitiva)*. |

**Responsable de Vitest y documentación:** completar con el nombre y las responsabilidades definitivas acordadas por el equipo antes de la entrega.

## 16. Repositorio y consideraciones finales

**Repositorio:** https://github.com/Lunalker/Proyecto-Aquachile

Este proyecto se ha desarrollado con fines académicos y de Vinculación con el Medio. Su finalidad es demostrar una solución técnicamente viable para centralizar información del proceso de evaluaciones psicolaborales, manteniendo una arquitectura comprensible, pruebas verificables y un flujo de trabajo trazable.

**No utilizar datos personales reales ni publicar contraseñas, archivos `.env` o configuraciones sensibles.**
