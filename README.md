# RI_P3_luceneindex
Pasos de Ejecución Actualizados
Paso 1: Configurar la estructura del proyecto
text
airbnb-indexer/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/
                └── example/
                    └── airbnb/
                        └── Indexer.java

# Crear directorio principal
mkdir airbnb-indexer
cd airbnb-indexer

# Crear estructura de directorios de Maven
mkdir -p src/main/java/com/example/airbnb
mkdir data
mkdir indexes

Paso 2: Colocar los archivos
pom.xml
Airbnb-lucene-indexer.java

Paso 3: Preparar los datos
Coloca tu archivo CSV con el formato específico en:
# Mueve tu archivo CSV al directorio data
mv /ruta/de/tu/archivo.csv data/airbnb_data.csv
text
airbnb-indexer/
└── data/
    └── airbnb_data.csv  # Tu archivo con el formato completo
Paso 4: Compilar el proyecto desde la caprpeta raiz
bash
mvn clean package

Paso 5: Ejecutar la aplicación
Para crear índice de propiedades:

# Para propiedades
java -jar target/airbnb-indexer-1.0.jar --action create --type property --input data/airbnb_data.csv --indexDir indexes/properties

# Para hosts
java -jar target/airbnb-indexer-1.0.jar --action create --type host --input data/airbnb_data.csv --indexDir indexes/hosts