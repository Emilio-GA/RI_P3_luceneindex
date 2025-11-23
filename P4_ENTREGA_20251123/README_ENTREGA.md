# Proyecto P4 - Indexador y Búsquedas Lucene Airbnb

## 📋 Información del Proyecto

Este proyecto implementa un sistema de indexación y búsqueda usando Apache Lucene 10.3.1 para datos de Airbnb en Los Angeles.

**Clases principales:**
- `AirbnbIndexador.java` - Indexa datos CSV en índices Lucene
- `BusquedasLucene.java` - Realiza búsquedas interactivas en los índices

## ⚙️ Requisitos

- **Java 21** o superior
- **Maven 3.6+** (para compilación y gestión de dependencias)

Verificar versiones:
```bash
java -version
mvn --version
```

## 📁 Estructura del Proyecto

```
.
├── src/main/java/          # Código fuente Java
│   ├── AirbnbIndexador.java
│   └── BusquedasLucene.java
├── bin/                    # JARs compilados y clases
│   ├── airbnb-indexer.jar
│   └── classes/
├── data/                   # Archivos CSV de datos
│   └── listings.csv (o example_listings.csv)
├── docs/                   # Documentación
│   ├── README.md
│   ├── BUILD_WITH_MAVEN.md
│   ├── LUKE_GUIDE.md
│   └── ...
├── indices/                # Índices Lucene (se generan al ejecutar)
│   ├── index_properties/
│   └── index_hosts/
├── scripts/                # Scripts de compilación y ejecución
│   ├── compilar.sh
│   ├── ejecutar-indexador.sh
│   ├── ejecutar-busquedas.sh
│   └── comando-critico.sh
└── pom.xml                 # Configuración Maven
```

## 🚀 Compilación Rápida

### Opción 1: Usar script automatizado
```bash
./scripts/compilar.sh
```

### Opción 2: Compilar manualmente
```bash
mvn clean compile
rm -f target/classes/BusquedasLucene.class
mvn dependency:build-classpath -DincludeScope=compile -q -Dmdep.outputFile=/tmp/cp.txt
javac -cp "target/classes:$(cat /tmp/cp.txt)" -d target/classes --release 21 src/main/java/BusquedasLucene.java
```

**Nota importante:** `BusquedasLucene.java` requiere compilación manual debido a problemas conocidos con Maven. El script `compilar.sh` automatiza este proceso.

## ▶️ Ejecución

### 1. Indexador (crear índices)

**Opción A: Usar script**
```bash
./scripts/ejecutar-indexador.sh --input ./data/listings.csv --index-root ./indices --mode build
```

**Opción B: Ejecutar directamente**
```bash
./scripts/compilar.sh
mvn dependency:build-classpath -DincludeScope=compile -q -Dmdep.outputFile=/tmp/cp.txt
java -cp "target/classes:$(cat /tmp/cp.txt)" AirbnbIndexador --input ./data/listings.csv --index-root ./indices --mode build
```

**Parámetros del indexador:**
- `--input`: Ruta al archivo CSV
- `--index-root`: Directorio donde se crearán los índices
- `--mode`: `build` (crear nuevo), `update` (actualizar), `rebuild` (reconstruir)
- `--threads`: Número de hilos (opcional, default: cores/2)
- `--max-errors`: Máximo de errores permitidos (opcional, default: 100)

### 2. Búsquedas (interactivo)

**Opción A: Usar script**
```bash
./scripts/ejecutar-busquedas.sh --index-root ./indices
```

**Opción B: Comando crítico completo (compilar y ejecutar)**
```bash
./scripts/comando-critico.sh --index-root ./indices
```

**Opción C: Ejecutar manualmente**
```bash
# Primero compilar (ver sección de compilación)
# Luego ejecutar:
mvn dependency:build-classpath -DincludeScope=compile -q -Dmdep.outputFile=/tmp/cp.txt
java -cp "target/classes:$(cat /tmp/cp.txt)" BusquedasLucene --index-root ./indices
```

## 🔍 Menú de Búsquedas

El programa de búsquedas ofrece un menú interactivo con las siguientes opciones:

1. **QueryParser** - Búsquedas textuales en diferentes campos
2. **Consultas Numéricas** - Búsquedas exactas y por rango
3. **BooleanQueries** - Consultas combinadas con operadores lógicos
4. **Consultas Ordenadas** - Ordenar resultados por criterios distintos al score
5. **Consultas Geográficas** - Búsquedas por distancia y ubicación
6. **Consultas Multi-Índice** - Búsquedas que combinan ambos índices

## ⚠️ Notas Importantes

### Problema conocido con BusquedasLucene

`BusquedasLucene.java` tiene un problema de compilación con Maven que requiere un workaround:
1. Compilar el resto del proyecto con `mvn clean compile`
2. Eliminar el `.class` generado incorrectamente: `rm -f target/classes/BusquedasLucene.class`
3. Compilar manualmente con `javac` usando el classpath de Maven

Los scripts incluidos (`compilar.sh` y `comando-critico.sh`) automatizan este proceso.

### Comando Crítico

El comando completo para compilar y ejecutar BusquedasLucene es:
```bash
mvn clean compile && \
rm -f target/classes/BusquedasLucene.class && \
mvn dependency:build-classpath -DincludeScope=compile -q -Dmdep.outputFile=/tmp/cp.txt && \
javac -cp "target/classes:$(cat /tmp/cp.txt)" -d target/classes --release 21 src/main/java/BusquedasLucene.java && \
java -cp "target/classes:$(cat /tmp/cp.txt)" BusquedasLucene --index-root ./indices
```

Este comando está disponible en `scripts/comando-critico.sh`.

## 📚 Documentación Adicional

- `docs/README.md` - Documentación general del indexador
- `docs/BUILD_WITH_MAVEN.md` - Guía de compilación con Maven
- `docs/LUKE_GUIDE.md` - Guía para usar Luke (herramienta de inspección de índices)
- `docs/RELACION_CLASES_LUCENE.md` - Relación entre clases de Lucene

## 🐛 Solución de Problemas

### Error: "No se encuentra la clase BusquedasLucene"
- Asegúrate de haber ejecutado `./scripts/compilar.sh` primero
- Verifica que `target/classes/BusquedasLucene.class` existe

### Error: "Java version not supported"
- Verifica que tienes Java 21: `java -version`
- El proyecto requiere Java 21 estrictamente

### Error: "Maven not found"
- Instala Maven: `sudo apt install maven` (Linux) o descarga desde https://maven.apache.org

### Los índices no se generan
- Verifica que el archivo CSV existe en `data/`
- Revisa los permisos de escritura en el directorio `indices/`
- Ejecuta el indexador con `--mode rebuild` para forzar recreación

## 📝 Información del Proyecto

**Proyecto:** P4 - Indexador y Búsquedas Lucene Airbnb  
**Asignatura:** Recuperación de Información  
**Año:** 2025-26
**Autores:** Felipe y Emilio

---

*Este paquete contiene el código fuente completo, scripts de compilación y documentación necesaria para compilar y ejecutar el proyecto en cualquier máquina con Java 21 y Maven 3.6+.*
