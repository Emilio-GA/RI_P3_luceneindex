import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Indexador Lucene para datos de Airbnb Los Angeles (Jun 2025)
 * 
 * Crea dos índices separados:
 * - index_properties/ : para propiedades/listings
 * - index_hosts/ : para anfitriones/hosts
 * 
 * Implementa upsert por ID, rebuild completo y logging.
 * 
 * COMPILACIÓN:
 *   javac -cp "lucene-10.3.1/modules/*:lucene-10.3.1/modules-thirdparty/*:lib/*" AirbnbIndexador.java
 * 
 * EJECUCIÓN:
 *   java -cp ".:lucene-10.3.1/modules/*:lucene-10.3.1/modules-thirdparty/*:lib/*" AirbnbIndexador --input example_listings.csv --index-root ./index_root [opciones]
 * 
 * LUKE:
 *  ./lucene-10.3.1/bin/luke.sh "$(pwd)/index_root/index_properties" &
 *  ./lucene-10.3.1/bin/luke.sh "$(pwd)/index_root/index_hosts" &
 * 
 * ARGUMENTOS CLI:
 *   --input <ruta>           : (OBLIGATORIO) Ruta al archivo CSV de entrada (ej: example_listings.csv)
 *   --index-root <carpeta>  : (OBLIGATORIO) Carpeta donde se crearán los índices (index_properties/ e index_hosts/)
 *   --mode <modo>           : Modo de indexación (build|update|rebuild). Default: build
 *                             - build: crea nuevos índices (borra existentes si hay)
 *                             - update: añade documentos a índices existentes (upsert)
 *                             - rebuild: reconstruye completamente los índices (con --force los borra primero)
 *   --delimiter <char>      : Delimitador CSV. Default: ","
 *   --encoding <charset>     : Codificación del archivo CSV. Default: "utf-8"
 *   --id-field <nombre>     : Nombre del campo ID en el CSV. Default: "id"
 *   --threads <n>           : Número de hilos para procesamiento. Default: cores/2
 *   --max-errors <n>        : Máximo número de errores antes de abortar. Default: 100
 *   --log-file <ruta>       : Archivo opcional para guardar logs (además de consola)
 *   --dry-run              : Simula la indexación sin escribir en los índices
 *   --force                : Fuerza el borrado completo de índices existentes (solo con --mode rebuild)
 */
public class AirbnbIndexador {

    // Configuración por defecto
    private static final String DEFAULT_MODE = "build";
    private static final String DEFAULT_DELIMITER = ",";
    private static final String DEFAULT_ENCODING = "utf-8";
    private static final String DEFAULT_ID_FIELD = "id";
    private static final int DEFAULT_MAX_ERRORS = 100;
    private static final int COMMIT_INTERVAL = 5000;
    
    // Constantes públicas para nombres de índices (reutilizables en búsquedas)
    public static final String INDEX_PROPERTIES = "index_properties";
    public static final String INDEX_HOSTS = "index_hosts";

    // Configuración de la aplicación
    private final Config config;
    
    // Writers para los dos índices
    private IndexWriter writerProperties;
    private IndexWriter writerHosts;
    
    // Mapeo de columnas del CSV
    private final Map<String, Integer> headerIndex = new HashMap<>();
    
    // Contadores
    private final AtomicInteger totalPropiedades = new AtomicInteger(0);
    private final AtomicInteger totalHosts = new AtomicInteger(0);
    private final AtomicInteger errores = new AtomicInteger(0);
    private final AtomicLong inicioTiempo = new AtomicLong(0);
    
    // Cache de hosts procesados para evitar duplicados
    private final Map<String, Document> hostsCache = new HashMap<>();

    /**
     * Configuración de parámetros CLI
     */
    public static class Config {
        String input;
        String indexRoot;
        String mode = DEFAULT_MODE;
        String delimiter = DEFAULT_DELIMITER;
        String encoding = DEFAULT_ENCODING;
        String idField = DEFAULT_ID_FIELD;
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        int maxErrors = DEFAULT_MAX_ERRORS;
        String logFile;
        boolean dryRun = false;
        boolean force = false;
    }

    public AirbnbIndexador(Config config) {
        this.config = config;
    }

    public static void main(String[] args) {
        try {
            Config config = parseArgs(args);
            
            // Validar parámetros obligatorios
            if (config.input == null || config.indexRoot == null) {
                System.err.println("Error: --input y --index-root son obligatorios");
                System.err.println("Uso: java -jar indexer.jar --input <ruta> --index-root <carpeta> [opciones]");
                System.exit(4);
            }

            AirbnbIndexador indexador = new AirbnbIndexador(config);
            indexador.ejecutar();
            System.exit(0);

        } catch (IllegalArgumentException e) {
            System.err.println("Error de parámetros: " + e.getMessage());
            System.exit(4);
        } catch (IOException e) {
            System.err.println("Error de I/O: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        } catch (Exception e) {
            System.err.println("Error inesperado: " + e.getMessage());
            e.printStackTrace();
            System.exit(5);
        }
    }

    /**
     * Ejecuta el proceso completo de indexación
     */
    public void ejecutar() throws Exception {
        inicioTiempo.set(System.currentTimeMillis());
        
        Logger logger = new Logger(config.logFile);
        logger.info("=== Iniciando indexación Airbnb ===");
        logger.info("Input: " + config.input);
        logger.info("Index root: " + config.indexRoot);
        logger.info("Mode: " + config.mode);
        logger.info("Threads: " + config.threads);

        try {
            // Configurar índices
            configurarIndices(logger);

            // Procesar CSV
            procesarCSV(logger);

            // Cerrar índices
            cerrarIndices(logger);

            // Resumen final
            long tiempoTotal = System.currentTimeMillis() - inicioTiempo.get();
            logger.info("=== Indexación completada ===");
            logger.info("Propiedades indexadas: " + totalPropiedades.get());
            logger.info("Hosts indexados: " + totalHosts.get());
            logger.info("Errores: " + errores.get());
            logger.info("Tiempo total: " + tiempoTotal + " ms");

            if (errores.get() > config.maxErrors) {
                logger.error("Superado max-errors (" + config.maxErrors + "). Abortando.");
                throw new RuntimeException("Demasiados errores: " + errores.get());
            }

        } finally {
            logger.close();
        }
    }

    /**
     * Configura los dos índices (propiedades y hosts)
     */
    private void configurarIndices(Logger logger) throws IOException {
        Path indexRootPath = Paths.get(config.indexRoot);
        if (!Files.exists(indexRootPath)) {
            Files.createDirectories(indexRootPath);
        }

        Path indexPathProperties = indexRootPath.resolve(INDEX_PROPERTIES);
        Path indexPathHosts = indexRootPath.resolve(INDEX_HOSTS);

        // Crear analizadores por campo
        Analyzer analyzer = crearAnalizador();

        // Configurar modo de apertura
        IndexWriterConfig.OpenMode openMode;
        if ("rebuild".equals(config.mode) && config.force) {
            // Borrar índices existentes
            if (Files.exists(indexPathProperties)) {
                Files.walk(indexPathProperties)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.delete(path); } catch (IOException e) {}
                    });
                logger.info("Índice de propiedades eliminado (rebuild --force)");
            }
            if (Files.exists(indexPathHosts)) {
                Files.walk(indexPathHosts)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.delete(path); } catch (IOException e) {}
                    });
                logger.info("Índice de hosts eliminado (rebuild --force)");
            }
            openMode = IndexWriterConfig.OpenMode.CREATE;
        } else if ("build".equals(config.mode) || "rebuild".equals(config.mode)) {
            openMode = IndexWriterConfig.OpenMode.CREATE;
        } else { // update
            openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND;
        }

        // Crear writers
        IndexWriterConfig iwcProperties = new IndexWriterConfig(analyzer);
        iwcProperties.setOpenMode(openMode);
        Directory dirProperties = FSDirectory.open(indexPathProperties);
        writerProperties = new IndexWriter(dirProperties, iwcProperties);

        IndexWriterConfig iwcHosts = new IndexWriterConfig(analyzer);
        iwcHosts.setOpenMode(openMode);
        Directory dirHosts = FSDirectory.open(indexPathHosts);
        writerHosts = new IndexWriter(dirHosts, iwcHosts);

        logger.info("Índices configurados correctamente");
    }

    /**
     * Crea el analizador con configuración por campo
     * Método público y estático para ser reutilizado en búsquedas
     */
    public static Analyzer crearAnalizador() {
        Analyzer defaultAnalyzer = new StandardAnalyzer();
        Map<String, Analyzer> perField = new HashMap<>();
        
        // Campos en inglés
        perField.put("description", new EnglishAnalyzer());
        perField.put("neighborhood_overview", new EnglishAnalyzer());
        perField.put("host_about", new EnglishAnalyzer());
        perField.put("host_location", new EnglishAnalyzer());
        
        // Analyzer personalizado para campos categóricos: keyword + lowercase
        Analyzer lowercaseKeywordAnalyzer = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                KeywordTokenizer tokenizer = new KeywordTokenizer();
                TokenStream filter = new LowerCaseFilter(tokenizer);
                return new TokenStreamComponents(tokenizer, filter);
            }
        };
        
        // Campos categóricos (keyword + lowercase para case-insensitive)
        perField.put("neighbourhood_cleansed", lowercaseKeywordAnalyzer);
        perField.put("property_type", lowercaseKeywordAnalyzer);
        perField.put("host_response_time", lowercaseKeywordAnalyzer);
        
        return new PerFieldAnalyzerWrapper(defaultAnalyzer, perField);
    }

    /**
     * Obtiene la ruta completa del índice de propiedades
     * @param indexRoot Directorio raíz donde están los índices
     * @return Path al índice de propiedades
     */
    public static Path getPropertiesIndexPath(String indexRoot) {
        return Paths.get(indexRoot, INDEX_PROPERTIES);
    }

    /**
     * Obtiene la ruta completa del índice de hosts
     * @param indexRoot Directorio raíz donde están los índices
     * @return Path al índice de hosts
     */
    public static Path getHostsIndexPath(String indexRoot) {
        return Paths.get(indexRoot, INDEX_HOSTS);
    }

    /**
     * Crea y devuelve la Similarity por defecto (BM25Similarity)
     * Para garantizar consistencia entre indexación y búsqueda
     * @return Similarity configurada
     */
    public static Similarity crearSimilarity() {
        return new BM25Similarity();
    }

    /**
     * Procesa el CSV en modo streaming
     * 
     * Asume:
     * - Un único archivo CSV (no procesa directorios)
     * - El archivo viene de config.input (ej: listings.csv)
     * - El parser CSV robusto es necesario porque campos como "Los Angeles, CA"
     *   y descripciones pueden contener comas dentro de comillas dobles
     * - Maneja filas multi-línea cuando campos contienen saltos de línea dentro de comillas
     */
    private void procesarCSV(Logger logger) throws IOException {
        Path csvPath = Paths.get(config.input);
        if (!Files.exists(csvPath)) {
            throw new IOException("Input no existe: " + csvPath.toAbsolutePath());
        }

        Charset charset = Charset.forName(config.encoding);

        try (BufferedReader br = Files.newBufferedReader(csvPath, charset)) {
            // Leer cabecera
            String headerLine = readCompleteCsvRow(br);
            if (headerLine == null) {
                logger.warn("Archivo vacío: " + csvPath);
                return;
            }

            parseHeader(headerLine);

            // Procesar filas
            String row;
            int count = 0;
            int commitCounter = 0;

            while ((row = readCompleteCsvRow(br)) != null) {
                try {
                    List<String> cols = parseCsvLine(row, config.delimiter);
                    procesarFila(cols, logger);
                    count++;
                    commitCounter++;

                    // Commit periódico
                    if (commitCounter >= COMMIT_INTERVAL) {
                        writerProperties.commit();
                        writerHosts.commit();
                        commitCounter = 0;
                        logger.debug("Commit realizado. Propiedades: " + totalPropiedades.get() + ", Hosts: " + totalHosts.get());
                    }

                } catch (Exception e) {
                    errores.incrementAndGet();
                    logger.error("Error procesando fila " + count + ": " + e.getMessage());
                    
                    if (errores.get() > config.maxErrors) {
                        throw new RuntimeException("Demasiados errores. Abortando.");
                    }
                }
            }

            // Commit final del archivo
            writerProperties.commit();
            writerHosts.commit();
            
            logger.info("Archivo procesado: " + count + " filas");
        }
    }

    /**
     * Lee una fila CSV completa que puede abarcar múltiples líneas.
     * Acumula líneas hasta que todas las comillas estén cerradas.
     */
    private String readCompleteCsvRow(BufferedReader br) throws IOException {
        String line = br.readLine();
        if (line == null) return null;
        
        // Si no hay comillas, es una fila simple de una línea
        int quoteCount = countUnescapedQuotes(line);
        if (quoteCount % 2 == 0) {
            return line;
        }
        
        // Hay comillas sin cerrar, acumular líneas hasta cerrar todas las comillas
        StringBuilder row = new StringBuilder(line);
        while (quoteCount % 2 != 0) {
            line = br.readLine();
            if (line == null) {
                // Fin de archivo sin cerrar comillas - devolver lo que tenemos
                break;
            }
            row.append('\n').append(line);
            quoteCount += countUnescapedQuotes(line);
        }
        
        return row.toString();
    }

    /**
     * Cuenta comillas no escapadas en una línea (ignora comillas escapadas "")
     */
    private int countUnescapedQuotes(String line) {
        if (line == null) return 0;
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') {
                // Verificar si es una comilla escapada (dos comillas consecutivas)
                if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    i++; // Saltar la siguiente comilla también
                } else {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Parsea la cabecera del CSV
     */
    private void parseHeader(String header) {
        List<String> cols = parseCsvLine(header, config.delimiter);
        headerIndex.clear();
        for (int i = 0; i < cols.size(); i++) {
            headerIndex.put(cols.get(i), i);
        }
    }

    /**
     * Procesa una fila del CSV: crea documentos para propiedades y hosts
     */
    private void procesarFila(List<String> cols, Logger logger) throws IOException {
        if (cols == null || cols.isEmpty()) return;

        // Extraer ID de propiedad (obligatorio)
        String idStr = get(cols, config.idField);
        if (idStr == null || idStr.isBlank()) {
            throw new IllegalArgumentException("Campo 'id' obligatorio faltante");
        }

        // Crear documento de propiedad
        Document docProperty = crearDocumentoPropiedad(cols);
        if (docProperty != null) {
            // Upsert por ID
            Term termId = new Term("id", idStr);
            if (config.dryRun) {
                logger.debug("DRY-RUN: Upsert propiedad ID=" + idStr);
            } else {
                writerProperties.updateDocument(termId, docProperty);
                totalPropiedades.incrementAndGet();
            }
        }

        // Extraer host_id (obligatorio para hosts)
        String hostId = get(cols, "host_id");
        if (hostId != null && !hostId.isBlank()) {
            // Verificar si ya procesamos este host en esta sesión
            if (!hostsCache.containsKey(hostId)) {
                Document docHost = crearDocumentoHost(cols);
                if (docHost != null) {
                    hostsCache.put(hostId, docHost);
                    
                    if (config.dryRun) {
                        logger.debug("DRY-RUN: Upsert host ID=" + hostId);
                    } else {
                        Term termHostId = new Term("host_id", hostId);
                        writerHosts.updateDocument(termHostId, docHost);
                        totalHosts.incrementAndGet();
                    }
                }
            }
        }
    }

    /**
     * Crea un documento Lucene para una propiedad
     */
    private Document crearDocumentoPropiedad(List<String> cols) {
        Document doc = new Document();

        // ID (IntPoint, no stored como punto, pero sí como StoredField para recuperación)
        String idStr = get(cols, config.idField);
        Integer id = parseInteger(idStr);
        if (id == null) {
            return null; // ID obligatorio
        }
        doc.add(new IntPoint("id", id));

        // listing_url (StringField, stored - URL)
        String listingUrl = get(cols, "listing_url");
        if (listingUrl != null && !listingUrl.isBlank()) {
            doc.add(new StringField("listing_url", listingUrl.trim(), Field.Store.YES));
        }

        // name (TextField, stored)
        addTextField(doc, "name", get(cols, "name"), true);

        // description (TextField con EnglishAnalyzer, stored)
        addTextField(doc, "description", htmlToText(get(cols, "description")), true);

        // neighborhood_overview (TextField con EnglishAnalyzer, stored)
        addTextField(doc, "neighborhood_overview", htmlToText(get(cols, "neighborhood_overview")), true);

        // neighbourhood_cleansed (FacetField para facetado + StringField para búsqueda)
        // Normalizar a lowercase para evitar problemas de case-sensitivity con KeywordAnalyzer
        String neighbourhood = get(cols, "neighbourhood_cleansed");
        if (neighbourhood != null && !neighbourhood.isBlank()) {
            String neighbourhoodNormalized = neighbourhood.trim().toLowerCase();
            // Guardar valor original para stored field
            doc.add(new StoredField("neighbourhood_cleansed_original", neighbourhood.trim()));
            doc.add(new FacetField("neighbourhood_cleansed", neighbourhoodNormalized));
            doc.add(new StringField("neighbourhood_cleansed", neighbourhoodNormalized, Field.Store.YES));
            doc.add(new SortedDocValuesField("neighbourhood_cleansed", new org.apache.lucene.util.BytesRef(neighbourhoodNormalized)));
        }

        // latitude / longitude (LatLonPoint + Stored + DocValues)
        Double lat = parseDouble(get(cols, "latitude"));
        Double lon = parseDouble(get(cols, "longitude"));
        if (lat != null && lon != null) {
            doc.add(new LatLonPoint("location", lat, lon));
            doc.add(new StoredField("latitude", lat));
            doc.add(new StoredField("longitude", lon));
            doc.add(new LatLonDocValuesField("location", lat, lon));
        }

        // property_type (FacetField para facetado + StringField para búsqueda)
        // Normalizar a lowercase para evitar problemas de case-sensitivity con KeywordAnalyzer
        String propertyType = get(cols, "property_type");
        if (propertyType != null && !propertyType.isBlank()) {
            String propertyTypeNormalized = propertyType.trim().toLowerCase();
            // Guardar valor original para stored field
            doc.add(new StoredField("property_type_original", propertyType.trim()));
            doc.add(new FacetField("property_type", propertyTypeNormalized));
            doc.add(new StringField("property_type", propertyTypeNormalized, Field.Store.YES));
            doc.add(new SortedDocValuesField("property_type", new org.apache.lucene.util.BytesRef(propertyTypeNormalized)));
        }

        // amenities (TextField multivaluado)
        String amenitiesRaw = get(cols, "amenities");
        if (amenitiesRaw != null && !amenitiesRaw.isBlank()) {
            // Parsear y indexar cada amenidad individual como campo multivaluado
            List<String> amenList = parseAmenities(amenitiesRaw);
            for (String amenity : amenList) {
                doc.add(new TextField("amenity", amenity, Field.Store.YES));
            }
        }

        // price (DoublePoint, stored + docvalues)
        Double price = parsePrice(get(cols, "price"));
        if (price != null) {
            doc.add(new DoublePoint("price", price));
            doc.add(new StoredField("price", price));
            doc.add(new DoubleDocValuesField("price", price));
        }

        // number_of_reviews (IntPoint, stored + docvalues)
        Integer numReviews = parseInteger(get(cols, "number_of_reviews"));
        if (numReviews != null) {
            doc.add(new IntPoint("number_of_reviews", numReviews));
            doc.add(new StoredField("number_of_reviews", numReviews));
            doc.add(new NumericDocValuesField("number_of_reviews", numReviews));
        }

        // review_scores_rating (DoublePoint, stored + docvalues)
        Double rating = parseDouble(get(cols, "review_scores_rating"));
        if (rating != null) {
            doc.add(new DoublePoint("review_scores_rating", rating));
            doc.add(new StoredField("review_scores_rating", rating));
            doc.add(new DoubleDocValuesField("review_scores_rating", rating));
        }

        // bathrooms (IntPoint, stored + docvalues)
        Double bathrooms = parseDouble(get(cols, "bathrooms"));
        if (bathrooms != null) {
            int bathroomsInt = bathrooms.intValue();
            doc.add(new IntPoint("bathrooms", bathroomsInt));
            doc.add(new StoredField("bathrooms", bathroomsInt));
            doc.add(new NumericDocValuesField("bathrooms", bathroomsInt));
        }

        // bathrooms_text (TextField, stored)
        addTextField(doc, "bathrooms_text", get(cols, "bathrooms_text"), true);

        // bedrooms (IntPoint, stored + docvalues)
        Integer bedrooms = parseInteger(get(cols, "bedrooms"));
        if (bedrooms != null) {
            doc.add(new IntPoint("bedrooms", bedrooms));
            doc.add(new StoredField("bedrooms", bedrooms));
            doc.add(new NumericDocValuesField("bedrooms", bedrooms));
        }

        // host_id (join lógico - StringField, stored + docvalues)
        String hostId = get(cols, "host_id");
        if (hostId != null && !hostId.isBlank()) {
            doc.add(new StringField("host_id", hostId, Field.Store.YES));
            doc.add(new SortedDocValuesField("host_id", new org.apache.lucene.util.BytesRef(hostId)));
        }

        return doc;
    }

    /**
     * Crea un documento Lucene para un host
     */
    private Document crearDocumentoHost(List<String> cols) {
        Document doc = new Document();

        // host_id (StringField, no stored como campo principal, pero sí docvalues)
        String hostId = get(cols, "host_id");
        if (hostId == null || hostId.isBlank()) {
            return null; // host_id obligatorio
        }
        doc.add(new StringField("host_id", hostId, Field.Store.NO));
        doc.add(new SortedDocValuesField("host_id", new org.apache.lucene.util.BytesRef(hostId)));

        // host_url (StringField, stored - URL)
        String hostUrl = get(cols, "host_url");
        if (hostUrl != null && !hostUrl.isBlank()) {
            doc.add(new StringField("host_url", hostUrl.trim(), Field.Store.YES));
        }

        // host_name (TextField, stored)
        addTextField(doc, "host_name", get(cols, "host_name"), true);

        // host_since (LongPoint + Stored - epoch millis + original)
        String hostSinceStr = get(cols, "host_since");
        Long hostSince = parseDate(hostSinceStr);
        if (hostSince != null) {
            doc.add(new LongPoint("host_since", hostSince));
            doc.add(new StoredField("host_since", hostSince));
            // Guardar también el valor original
            if (hostSinceStr != null) {
                doc.add(new StoredField("host_since_original", hostSinceStr));
            }
            doc.add(new NumericDocValuesField("host_since", hostSince));
        }

        // host_location (TextField con EnglishAnalyzer, no stored)
        addTextField(doc, "host_location", get(cols, "host_location"), false);

        // host_neighbourhood (TextField, stored)
        addTextField(doc, "host_neighbourhood", get(cols, "host_neighbourhood"), true);

        // host_about (TextField con EnglishAnalyzer, stored)
        addTextField(doc, "host_about", htmlToText(get(cols, "host_about")), true);

        // host_response_time (FacetField para facetado + StringField para búsqueda)
        // Normalizar a lowercase para evitar problemas de case-sensitivity con KeywordAnalyzer
        String responseTime = get(cols, "host_response_time");
        if (responseTime != null && !responseTime.isBlank()) {
            String responseTimeNormalized = responseTime.trim().toLowerCase();
            // Guardar valor original para stored field
            doc.add(new StoredField("host_response_time_original", responseTime.trim()));
            doc.add(new FacetField("host_response_time", responseTimeNormalized));
            doc.add(new StringField("host_response_time", responseTimeNormalized, Field.Store.YES));
            doc.add(new SortedDocValuesField("host_response_time", new org.apache.lucene.util.BytesRef(responseTimeNormalized)));
        }

        // host_is_superhost (IntPoint 0/1, stored + docvalues)
        String superhostStr = get(cols, "host_is_superhost");
        int superhostValue = normalizeSuperhost(superhostStr);
        doc.add(new IntPoint("host_is_superhost", superhostValue));
        doc.add(new StoredField("host_is_superhost", superhostValue));
        doc.add(new NumericDocValuesField("host_is_superhost", superhostValue));

        return doc;
    }

    /**
     * Normaliza host_is_superhost a 0 o 1
     */
    private int normalizeSuperhost(String value) {
        if (value == null) return 0;
        String lower = value.toLowerCase().trim();
        if ("t".equals(lower) || "true".equals(lower) || "yes".equals(lower) || "1".equals(lower)) {
            return 1;
        }
        return 0;
    }

    /**
     * Cierra los índices
     */
    private void cerrarIndices(Logger logger) throws IOException {
        if (writerProperties != null) {
            writerProperties.commit();
            writerProperties.close();
            logger.info("Índice de propiedades cerrado");
        }
        if (writerHosts != null) {
            writerHosts.commit();
            writerHosts.close();
            logger.info("Índice de hosts cerrado");
        }
    }


    // ===================== Utilidades =====================

    /**
     * Parsea argumentos CLI
     */
    private static Config parseArgs(String[] args) {
        Config config = new Config();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String value = (i + 1 < args.length && !args[i + 1].startsWith("--")) 
                    ? args[++i] : "";
                
                switch (arg) {
                    case "--input":
                        config.input = value;
                        break;
                    case "--index-root":
                        config.indexRoot = value;
                        break;
                    case "--mode":
                        config.mode = value.isEmpty() ? DEFAULT_MODE : value;
                        break;
                    case "--delimiter":
                        config.delimiter = value.isEmpty() ? DEFAULT_DELIMITER : value;
                        break;
                    case "--encoding":
                        config.encoding = value.isEmpty() ? DEFAULT_ENCODING : value;
                        break;
                    case "--id-field":
                        config.idField = value.isEmpty() ? DEFAULT_ID_FIELD : value;
                        break;
                    case "--threads":
                        try {
                            config.threads = value.isEmpty() ? 
                                Runtime.getRuntime().availableProcessors() / 2 : 
                                Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            config.threads = Runtime.getRuntime().availableProcessors() / 2;
                        }
                        break;
                    case "--max-errors":
                        try {
                            config.maxErrors = value.isEmpty() ? DEFAULT_MAX_ERRORS : Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            config.maxErrors = DEFAULT_MAX_ERRORS;
                        }
                        break;
                    case "--log-file":
                        config.logFile = value;
                        break;
                    case "--dry-run":
                        config.dryRun = true;
                        i--; // No consumir siguiente argumento
                        break;
                    case "--force":
                        config.force = true;
                        i--;
                        break;
                }
            }
        }
        return config;
    }

    /**
     * Parsea una línea CSV respetando comillas
     */
    private static List<String> parseCsvLine(String line, String delimiter) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (delimiter.equals(String.valueOf(c)) && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /**
     * Obtiene el valor de una columna por nombre
     */
    private String get(List<String> cols, String name) {
        Integer idx = headerIndex.get(name);
        if (idx == null || idx < 0 || idx >= cols.size()) return null;
        String v = cols.get(idx);
        return (v == null || v.isEmpty()) ? null : v;
    }

    /**
     * Parsea un entero
     */
    private static Integer parseInteger(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parsea un double
     */
    private static Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parsea un precio (limpia $ y comas)
     */
    private static Double parsePrice(String s) {
        if (s == null || s.isBlank()) return null;
        String clean = s.replace("$", "").replace(",", "").trim();
        return parseDouble(clean);
    }

    /**
     * Parsea una fecha a epoch millis
     */
    private static Long parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
            return sdf.parse(s.trim()).getTime();
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * Limpia HTML básico de texto
     */
    private static String htmlToText(String s) {
        if (s == null) return null;
        return s.replace("<br />", " ")
                .replace("<br>", " ")
                .replace("&nbsp;", " ");
    }

    /**
     * Parsea amenities (array estilo JSON)
     */
    private static List<String> parseAmenities(String raw) {
        List<String> res = new ArrayList<>();
        if (raw == null || raw.isBlank()) return res;
        
        String s = raw.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < s.length() && s.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                String amenity = cur.toString().trim();
                if (amenity.startsWith("\"") && amenity.endsWith("\"")) {
                    amenity = amenity.substring(1, amenity.length() - 1);
                }
                amenity = amenity.replace("\\\"", "\"");
                if (!amenity.isEmpty()) {
                    res.add(amenity);
                }
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        
        // Último elemento
        String amenity = cur.toString().trim();
        if (amenity.startsWith("\"") && amenity.endsWith("\"")) {
            amenity = amenity.substring(1, amenity.length() - 1);
        }
        amenity = amenity.replace("\\\"", "\"");
        if (!amenity.isEmpty()) {
            res.add(amenity);
        }
        
        return res;
    }

    /**
     * Añade un TextField al documento
     */
    private static void addTextField(Document doc, String field, String value, boolean store) {
        if (value == null || value.isBlank()) return;
        doc.add(new TextField(field, value, store ? Field.Store.YES : Field.Store.NO));
    }

    /**
     * Clase simple para logging
     */
    private static class Logger {
        private final PrintWriter logWriter;
        
        public Logger(String logFile) throws IOException {
            if (logFile != null && !logFile.isEmpty()) {
                logWriter = new PrintWriter(new FileWriter(logFile, true));
            } else {
                logWriter = null;
            }
        }
        
        public void info(String msg) {
            String fullMsg = "[INFO] " + msg;
            System.out.println(fullMsg);
            if (logWriter != null) {
                logWriter.println(fullMsg);
                logWriter.flush();
            }
        }
        
        public void warn(String msg) {
            String fullMsg = "[WARN] " + msg;
            System.err.println(fullMsg);
            if (logWriter != null) {
                logWriter.println(fullMsg);
                logWriter.flush();
            }
        }
        
        public void error(String msg) {
            String fullMsg = "[ERROR] " + msg;
            System.err.println(fullMsg);
            if (logWriter != null) {
                logWriter.println(fullMsg);
                logWriter.flush();
            }
        }
        
        public void debug(String msg) {
            String fullMsg = "[DEBUG] " + msg;
            System.out.println(fullMsg);
            if (logWriter != null) {
                logWriter.println(fullMsg);
                logWriter.flush();
            }
        }
        
        public void close() {
            if (logWriter != null) {
                logWriter.close();
            }
        }
    }
}

