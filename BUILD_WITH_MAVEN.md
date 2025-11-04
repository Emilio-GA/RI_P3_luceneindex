# Compilar con Maven

## Prerequisitos

1. Instalar Maven:
```bash
sudo apt install maven
# O descargar desde: https://maven.apache.org/download.cgi
```

2. Verificar instalación:
```bash
mvn --version
```

## Compilar y crear JAR

### 1. Compilar el proyecto:
```bash
mvn clean compile
```

### 2. Crear el JAR ejecutable (fat JAR con todas las dependencias):
```bash
mvn clean package
```

Esto generará el archivo `target/airbnb-indexer.jar` que contiene todas las dependencias incluidas.

## Ejecutar el JAR

Una vez creado el JAR, puedes ejecutarlo directamente sin necesidad de classpath:

```bash
java -jar target/airbnb-indexer.jar --input example_listings.csv --index-root ./index_root --mode rebuild --force
```

## Ventajas del JAR generado

- ✅ **Todo incluido**: No necesitas `lib/` ni `lucene-10.3.1/modules/` en el classpath
- ✅ **Portable**: Un solo archivo JAR que puedes copiar a cualquier máquina con Java 11+
- ✅ **Fácil de distribuir**: Solo necesitas el JAR y ejecutarlo con `java -jar`
- ✅ **Sin dependencias externas**: Todas las dependencias están dentro del JAR

## Estructura del proyecto

```
P3/
├── src/main/java/
│   └── AirbnbIndexador.java
├── pom.xml
├── target/
│   └── airbnb-indexer.jar  (generado por Maven)
└── ...
```

## Nota sobre lib/

Una vez que uses el JAR generado con Maven, ya **no necesitas** la carpeta `lib/` porque Gson está incluido en el JAR.

Pero puedes mantenerla si prefieres seguir usando el método manual de compilación.

