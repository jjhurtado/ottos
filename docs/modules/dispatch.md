# Módulo `dispatch` — Asignación y entregas

Paquete `org.jobits.ottos.dispatch`. **Vacío por ahora.**

## Para qué servirá

Asignación de remesas a repartidores por zona, rutas, prueba de entrega (firma, código, foto) y gestión de entregas.

## Estado

- La asignación manual vive en `remittances` (`POST /remittances/{id}/assign`, `GET /couriers`), igual que las
  incidencias de entrega.
- **Asignación por zona:** fuera de la versión 1.0.
- **Prueba de entrega con foto o firma:** Fase 3 (necesita almacenamiento de archivos, S3 o compatible).
- Mapas y geolocalización: fuera del roadmap.
