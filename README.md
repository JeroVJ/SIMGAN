# SIMGAN

SIMGAN es una plataforma para gestión ganadera apoyada en análisis satelital e IoT.  
Permite administrar fincas, terrenos y parcelas, gestionar lotes de ganado, monitorear humedad de suelo con sensores y generar alertas y reportes para apoyar decisiones de pastoreo rotacional.

---

# Funcionalidades principales

- Gestión de fincas, terrenos y parcelas con geometría geoespacial
- Rotación de potreros por estado operativo
- Gestión de lotes y ganado
- Monitoreo de sensores IoT por MQTT
- Análisis NDVI con imágenes satelitales
- Alertas automáticas por condiciones críticas
- Generación de reportes PDF por terreno

---

# Arquitectura general

SIMGAN está compuesto por tres partes:

- Frontend web en React y Vite
- Backend de negocio en Spring Boot
- Servicio de procesamiento NDVI en Python (FastAPI + Celery)

Además, integra servicios externos como broker MQTT, proveedores satelitales y SMTP para correo.

---

# Estructura del proyecto

```text
backend/              API principal y lógica de negocio
frontend/             Aplicación web
Processing/           Servicio de procesamiento satelital
database/             Scripts SQL de inicialización
docker-compose.yml    Orquestación de servicios base

 

