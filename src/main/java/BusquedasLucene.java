import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.Facets;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.LabelAndValue;
import org.apache.lucene.facet.taxonomy.FastTaxonomyFacetCounts;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.range.LongRangeFacetCounts;
import org.apache.lucene.facet.range.LongRange;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Clase básica de búsqueda Lucene para el índice de Airbnb
 * 
 * Busca en el índice de propiedades (index_properties) creado por
 * AirbnbIndexador.
 * 
 * COMPILACIÓN:
 * NOTA: Maven tiene problemas compilando esta clase directamente. Use este
 * workaround:
 * 
 * 1. Compile el resto del proyecto con Maven:
 * mvn clean compile
 * 
 * 2. CRÍTICO: Elimine el archivo compilado anterior (Maven genera uno
 * incorrecto):
 * rm -f target/classes/BusquedasLucene.class
 * 
 * 3. Compile BusquedasLucene manualmente con Java 21:
 * mvn dependency:build-classpath -DincludeScope=compile -q
 * -Dmdep.outputFile=/tmp/cp.txt
 * javac -cp "target/classes:$(cat /tmp/cp.txt)" -d target/classes --release 21
 * src/main/java/BusquedasLucene.java
 * 
 * COMANDO COMPLETO (todos los pasos en uno):
 * mvn clean compile && rm -f target/classes/BusquedasLucene.class && mvn
 * dependency:build-classpath -DincludeScope=compile -q
 * -Dmdep.outputFile=/tmp/cp.txt && javac -cp "target/classes:$(cat
 * /tmp/cp.txt)" -d target/classes --release 21
 * src/main/java/BusquedasLucene.java
 * 
 * EJECUCIÓN:
 * NOTA: mvn exec:java no funciona debido al mismo problema de compilación.
 * IMPORTANTE: NO ejecute "mvn dependency:build-classpath" de nuevo, ya que
 * recompila
 * y sobrescribe el archivo correcto. Use el classpath generado en el paso 3.
 * 
 * Ejecutar directamente con java (el classpath ya está en /tmp/cp.txt del paso
 * 3):
 * java -cp "target/classes:$(cat /tmp/cp.txt)" BusquedasLucene --index-root
 * ./index_root
 * 
 * COMANDO COMPLETO (compilar y ejecutar en uno):
 * mvn clean compile && rm -f target/classes/BusquedasLucene.class && mvn
 * dependency:build-classpath -DincludeScope=compile -q
 * -Dmdep.outputFile=/tmp/cp.txt && javac -cp "target/classes:$(cat
 * /tmp/cp.txt)" -d target/classes --release 21
 * src/main/java/BusquedasLucene.java && java -cp "target/classes:$(cat
 * /tmp/cp.txt)" BusquedasLucene --index-root ./index_root
 * 
 * Si necesita regenerar el classpath (sin recompilar), puede usar:
 * mvn dependency:build-classpath -DincludeScope=compile -q
 * -Dmdep.outputFile=/tmp/cp.txt -DskipTests
 * Pero luego DEBE recompilar manualmente BusquedasLucene de nuevo (paso 3)
 */
public class BusquedasLucene {

    // Número máximo de resultados a retornar en las búsquedas
    private static final int MAX_RESULTADOS_BUSQUEDA = 10;

    // Ubicaciones de los índices
    private String indexRoot;
    private String indexPathProperties;
    private String indexPathHosts;

    public BusquedasLucene(String indexRoot) {
        this.indexRoot = indexRoot;
        // Reutilizar método del indexador para garantizar consistencia
        this.indexPathProperties = AirbnbIndexador.getPropertiesIndexPath(indexRoot).toString();
        this.indexPathHosts = AirbnbIndexador.getHostsIndexPath(indexRoot).toString();
    }

    public static void main(String[] args) {
        String indexRoot = "./index_root";

        // Parsear argumentos simples
        for (int i = 0; i < args.length; i++) {
            if ("--index-root".equals(args[i]) && i + 1 < args.length) {
                indexRoot = args[i + 1];
                i++;
            }
        }

        BusquedasLucene busqueda = new BusquedasLucene(indexRoot);

        // Reutilizar el analizador y similarity del indexador para garantizar
        // consistencia
        Analyzer analyzer = AirbnbIndexador.crearAnalizador();
        Similarity similarity = AirbnbIndexador.crearSimilarity();

        // Búsqueda en el índice
        busqueda.indexSearch(analyzer, similarity);
    }

    // ====================================================
    // Asumimos que en el índice hay campos como:
    // - name: TextField, almacenado
    // - description: TextField, almacenado
    // - id: IntPoint, almacenado como StoredField
    // - price: DoublePoint, almacenado
    // - review_scores_rating: DoublePoint, almacenado
    // - property_type: StringField, almacenado
    // - neighbourhood_cleansed: StringField, almacenado

    public void indexSearch(Analyzer analyzer, Similarity similarity) {
        IndexReader reader = null;
        try {
            reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
            IndexSearcher searcher = new IndexSearcher(reader);
            // Asignamos cómo se calcula la similitud entre documentos
            searcher.setSimilarity(similarity);

            BufferedReader in = null;
            in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

            // ====================================================
            // BOILERPLATE PARA DIFERENTES TIPOS DE QUERIES
            // ====================================================

            while (true) {
                System.out.println("\n=== MENÚ PRINCIPAL ===");
                System.out.println("1. Crear consultas utilizando QueryParser para cada uno de los dos índices");
                System.out.println("2. Crear consultas que involucren valores numéricos (exactas y por rango)");
                System.out.println("3. Crear BooleanQuerys con distintos campos y BooleanClause");
                System.out.println("4. Crear consultas ordenadas por criterio distinto al score");
                System.out.println("5. Crear consultas geográficas");
                System.out.println("6. Crear consultas que involucren dos índices simultáneamente");
                System.out.println("7. Busqueda general (Mega Campo)");
                System.out.println("0. Salir");
                System.out.print("Selecciona opción: ");

                String option = in.readLine();
                if (option == null || option.trim().isEmpty() || "0".equals(option.trim())) {
                    break;
                }

                try {
                    switch (option.trim()) {
                        case "1":
                            menuQueryParser(analyzer, similarity, in);
                            break;
                        case "2":
                            menuQueriesNumericas(analyzer, similarity, in);
                            break;
                        case "3":
                            menuBooleanQueries(analyzer, similarity, in);
                            break;
                        case "4":
                            menuQueriesOrdenadas(analyzer, similarity, in);
                            break;
                        case "5":
                            menuQueriesGeograficas(analyzer, similarity, in);
                            break;
                        case "6":
                            menuQueriesMultiIndice(analyzer, similarity, in);
                            break;
                        case "7":
                            menuBusquedaGeneral(analyzer, similarity, in);
                            break;
                        default:
                            System.out.println("Opción no válida.");
                            continue;
                    }
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            reader.close();
        } catch (IOException e) {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    // ====================================================
    // MENÚS Y MÉTODOS PARA DIFERENTES TIPOS DE QUERIES
    // ====================================================

    /**
     * Menú para consultas usando QueryParser en los dos índices
     */
    private void menuQueryParser(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException {
        while (true) {
            System.out.println("\n=== 1. CONSULTAS CON QUERYPARSER ===");
            System.out.println("1.1. Buscar en campo 'neighbourhood_cleansed' en índice de Properties");
            System.out.println("1.2. Buscar en campo 'amenity' en índice de Properties");
            System.out.println("1.3. Buscar en campo 'host_name' en índice de Hosts");
            System.out.println("1.4. Buscar en campo 'host_about' en índice de Hosts");
            System.out.println("0. Volver al menú principal");
            System.out.print("Selecciona opción: ");

            String subOption = in.readLine();
            if (subOption == null || subOption.trim().isEmpty() || "0".equals(subOption.trim())) {
                break;
            }

            try {
                switch (subOption.trim()) {
                    case "1.1":
                    case "1":
                        ejecutarQueryNeighbourhoodProperties(analyzer, similarity, in);
                        break;
                    case "1.2":
                    case "2":
                        ejecutarQueryAmenityProperties(analyzer, similarity, in);
                        break;
                    case "1.3":
                    case "3":
                        ejecutarQueryHostName(analyzer, similarity, in);
                        break;
                    case "1.4":
                    case "4":
                        ejecutarQueryHostAbout(analyzer, similarity, in);
                        break;
                    default:
                        System.out.println("Opción no válida.");
                        continue;
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 1.1: Buscar en campo 'neighbourhood_cleansed' en índice de Properties
     */
    private void ejecutarQueryNeighbourhoodProperties(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 1.1: Búsqueda en 'neighbourhood_cleansed' (Properties) ===");
        System.out.print("Ingrese el valor a buscar en neighbourhood_cleansed: ");
        String valor = in.readLine();

        if (valor == null || valor.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        // Lógica de búsqueda
        QueryParser parser = new QueryParser("neighbourhood_cleansed", analyzer);
        Query query = parser.parse(valor.trim());
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
        mostrarResultados(searcher, hits);

        System.out.println("Búsqueda implementada para: " + valor.trim());
        System.out.println("Índice: Properties");
        System.out.println("Campo: neighbourhood_cleansed");
    }

    /**
     * 1.2: Buscar en campo 'amenity' en índice de Properties
     */
    private void ejecutarQueryAmenityProperties(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 1.2: Búsqueda en 'amenity' (Properties) ===");
        System.out.print("Ingrese el valor a buscar en amenity: ");
        String valor = in.readLine();

        if (valor == null || valor.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        // Lógica de búsqueda
        QueryParser parser = new QueryParser("amenity", analyzer);
        Query query = parser.parse(valor.trim());
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
        mostrarResultados(searcher, hits);

        System.out.println("Búsqueda implementada para: " + valor.trim());
        System.out.println("Índice: Properties");
        System.out.println("Campo: amenity");
    }

    /**
     * 1.3: Buscar en campo 'host_name' en índice de Hosts
     */
    private void ejecutarQueryHostName(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 1.3: Búsqueda en 'host_name' (Hosts) ===");
        System.out.print("Ingrese el valor a buscar en host_name (ej: 'Anahita'): ");
        String valor = in.readLine();

        if (valor == null || valor.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        // Lógica de búsqueda
        QueryParser parser = new QueryParser("host_name", analyzer);
        Query query = parser.parse(valor.trim());
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathHosts)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
        mostrarResultadosHosts(searcher, hits);
        reader.close();

        System.out.println("Búsqueda implementada para: " + valor.trim());
        System.out.println("Índice: Hosts");
        System.out.println("Campo: host_name");
    }

    /**
     * 1.4: Buscar en campo 'host_about' en índice de Hosts
     */
    private void ejecutarQueryHostAbout(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 1.4: Búsqueda en 'host_about' (Hosts) ===");
        System.out.print("Ingrese el valor a buscar en host_about (ej: 'yoga'): ");
        String valor = in.readLine();

        if (valor == null || valor.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        // Lógica de búsqueda
        QueryParser parser = new QueryParser("host_about", analyzer);
        Query query = parser.parse(valor.trim());
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathHosts)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
        mostrarResultadosHosts(searcher, hits);
        reader.close();

        System.out.println("Búsqueda implementada para: " + valor.trim());
        System.out.println("Índice: Hosts");
        System.out.println("Campo: host_about");
    }

    /**
     * Menú para consultas numéricas (exactas y por rango)
     */
    private void menuQueriesNumericas(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException {
        while (true) {
            System.out.println("\n=== 2. CONSULTAS NUMÉRICAS ===");
            System.out.println("2.1. Búsqueda en campo 'price' (Properties) - operadores: =, >, >=, <, <=");
            System.out.println("2.2. Búsqueda por rango en campo 'price' (Properties) - formato MIN-MAX");
            System.out.println("2.3. Búsqueda exacta en campo 'host_is_superhost' (Hosts) - 0 o 1");
            System.out.println("2.4. Búsqueda de hosts más antiguos que una fecha en 'host_since' (Hosts)");
            System.out.println("0. Volver al menú principal");
            System.out.print("Selecciona opción: ");

            String subOption = in.readLine();
            if (subOption == null || subOption.trim().isEmpty() || "0".equals(subOption.trim())) {
                break;
            }

            try {
                switch (subOption.trim()) {
                    case "2.1":
                    case "1":
                        ejecutarQueryPrecioExacto(analyzer, similarity, in);
                        break;
                    case "2.2":
                    case "2":
                        ejecutarQueryPrecioRango(analyzer, similarity, in);
                        break;
                    case "2.3":
                    case "3":
                        ejecutarQuerySuperhostExacto(analyzer, similarity, in);
                        break;
                    case "2.4":
                    case "4":
                        ejecutarQueryHostSinceRango(analyzer, similarity, in);
                        break;
                    default:
                        System.out.println("Opción no válida.");
                        continue;
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 2.1: Búsqueda en campo 'price' en índice de Properties con operadores de
     * comparación
     * Formatos aceptados:
     * - 120 o =120: precio igual a 120
     * - >120: precio mayor que 120
     * - >=120: precio mayor o igual a 120
     * - <120: precio menor que 120
     * - <=120: precio menor o igual a 120
     */
    private void ejecutarQueryPrecioExacto(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException {
        System.out.println("\n=== 2.1: Búsqueda en 'price' (Properties) ===");
        System.out.println("Formatos aceptados:");
        System.out.println("  - 120 o =120: precio igual a 120");
        System.out.println("  - >120: precio mayor que 120");
        System.out.println("  - >=120: precio mayor o igual a 120");
        System.out.println("  - <120: precio menor que 120");
        System.out.println("  - <=120: precio menor o igual a 120");
        System.out.print("Ingrese el precio con operador (ej: >120, =120, 120): ");
        String valorStr = in.readLine();

        if (valorStr == null || valorStr.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        try {
            String input = valorStr.trim();
            // Usar helper para extraer el valor numérico
            double precio = parseDoubleValue(input);
            Query query;
            String operador;

            // Solo detectar operador para crear la query correcta
            if (input.startsWith(">=")) {
                operador = ">=";
                query = DoublePoint.newRangeQuery("price", precio, Double.MAX_VALUE);
            } else if (input.startsWith("<=")) {
                operador = "<=";
                query = DoublePoint.newRangeQuery("price", 0.0, precio);
            } else if (input.startsWith(">")) {
                operador = ">";
                // Precio mayor: desde precio+epsilon hasta Double.MAX_VALUE
                double precioMin = precio + 0.01;
                query = DoublePoint.newRangeQuery("price", precioMin, Double.MAX_VALUE);
            } else if (input.startsWith("<")) {
                operador = "<";
                // Precio menor: desde 0 hasta precio-epsilon
                double precioMax = precio - 0.01;
                query = DoublePoint.newRangeQuery("price", 0.0, precioMax);
            } else if (input.startsWith("=")) {
                operador = "=";
                query = DoublePoint.newRangeQuery("price", precio, precio);
            } else {
                // Sin operador explícito, asumir igualdad
                operador = "=";
                query = DoublePoint.newRangeQuery("price", precio, precio);
            }

            // Logica de busqueda
            IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(similarity);
            TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
            mostrarResultados(searcher, hits);
            reader.close();

            System.out.println("Búsqueda implementada para precio " + operador + " $" +
                    String.format("%.2f", precio));
            System.out.println("Índice: Properties");
            System.out.println("Campo: price");
        } catch (NumberFormatException e) {
            System.out.println("Error: debe ingresar un número válido con formato correcto.");
            System.out.println("Ejemplos válidos: 120, =120, >120, >=120, <120, <=120");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * 2.2: Búsqueda por rango en campo 'price' en índice de Properties
     * Formato de entrada: "MIN-MAX" (ej: "100-200")
     */
    private void ejecutarQueryPrecioRango(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException {
        System.out.println("\n=== 2.2: Búsqueda por rango en 'price' (Properties) ===");
        System.out.print("Ingrese el rango de precio en formato MIN-MAX (ej: 100-200): ");
        String rangoStr = in.readLine();

        if (rangoStr == null || rangoStr.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        try {
            String[] partes = rangoStr.trim().split("-");
            if (partes.length != 2) {
                System.out.println("Error: formato incorrecto. Use MIN-MAX (ej: 100-200)");
                return;
            }

            double min = Double.parseDouble(partes[0].trim());
            double max = Double.parseDouble(partes[1].trim());

            if (min > max) {
                System.out.println("Error: el valor mínimo no puede ser mayor que el máximo.");
                return;
            }

            // Lógica de búsqueda
            Query query = DoublePoint.newRangeQuery("price", min, max);
            IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(similarity);
            TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
            mostrarResultados(searcher, hits);
            reader.close();

            System.out.println("Búsqueda implementada para rango de precio: $" + min + " - $" + max);
            System.out.println("Índice: Properties");
            System.out.println("Campo: price");
        } catch (NumberFormatException e) {
            System.out.println("Error: los valores MIN y MAX deben ser números válidos.");
        }
    }

    /**
     * 2.3: Búsqueda exacta en campo 'host_is_superhost' en índice de Hosts
     * Valores válidos: 0 (no es superhost) o 1 (es superhost)
     */
    private void ejecutarQuerySuperhostExacto(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException {
        System.out.println("\n=== 2.3: Búsqueda exacta en 'host_is_superhost' (Hosts) ===");
        System.out.print("Ingrese el valor (0/no = no superhost, 1/si = superhost): ");
        String valorStr = in.readLine();

        if (valorStr == null || valorStr.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        int valor;
        String valorNormalizado = valorStr.trim().toLowerCase();

        // Aceptar "si", "no", "1" o "0"
        if (valorNormalizado.equals("si") || valorNormalizado.equals("1")) {
            valor = 1;
        } else if (valorNormalizado.equals("no") || valorNormalizado.equals("0")) {
            valor = 0;
        } else {
            System.out.println("Error: el valor debe ser 0, 1, 'si' o 'no'.");
            return;
        }

        // Lógica de búsqueda
        Query query = IntPoint.newRangeQuery("host_is_superhost", valor, valor);
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathHosts)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
        mostrarResultadosHosts(searcher, hits);
        reader.close();

        System.out.println("Búsqueda implementada para host_is_superhost: " + valor +
                (valor == 1 ? " (superhost)" : " (no superhost)"));
        System.out.println("Índice: Hosts");
        System.out.println("Campo: host_is_superhost");
    }

    /**
     * 2.4: Búsqueda de hosts más antiguos que una fecha en campo 'host_since'
     * Formato de entrada: "YYYY-MM-DD" (ej: "2015-01-01")
     * Busca hosts con host_since < fecha_ingresada
     */
    private void ejecutarQueryHostSinceRango(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException {
        System.out.println("\n=== 2.4: Búsqueda de hosts más antiguos que una fecha (Hosts) ===");
        System.out.println("Formato: YYYY-MM-DD");
        System.out.println("Ejemplo: 2015-01-01");
        System.out.println("Nota: Busca hosts con host_since anterior a la fecha ingresada");
        System.out.print("Ingrese la fecha: ");
        String fechaStr = in.readLine();

        if (fechaStr == null || fechaStr.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        try {
            String trimmed = fechaStr.trim();
            long fechaLimite = parseDate(trimmed);

            // Buscar hosts con host_since < fechaLimite (más antiguos)
            // Usamos Long.MIN_VALUE como mínimo para incluir todos los hosts posibles
            // y fechaLimite-1 como máximo para excluir la fecha exacta (más antiguos que)
            long fechaLimiteExclusiva = fechaLimite - 1;
            Query query = LongPoint.newRangeQuery("host_since", Long.MIN_VALUE, fechaLimiteExclusiva);

            IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathHosts)));
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(similarity);
            TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
            mostrarResultadosHosts(searcher, hits);
            reader.close();

            System.out.println("Búsqueda implementada para hosts más antiguos que: " + trimmed);
            System.out.println("Índice: Hosts");
            System.out.println("Campo: host_since");
        } catch (DateTimeParseException e) {
            System.out.println("Error: formato de fecha inválido. Use YYYY-MM-DD (ej: 2015-01-01)");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Menú para BooleanQueries
     */
    private void menuBooleanQueries(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException {
        while (true) {
            System.out.println("\n=== 3. BOOLEANQUERIES ===");
            System.out.println("3.1. BooleanQuery: numérica AND textual (ej: rating >= 4.7 AND amenity 'pool')");
            System.out.println(
                    "3.2. BooleanQuery con MUST_NOT (ej: property_type MUST_NOT 'Entire home' AND number_of_reviews = 0)");
            System.out
                    .println("3.3. BooleanQuery con SHOULD (ej: neighbourhood_cleansed 'Hollywood' OR bedrooms >= 3)");
            System.out.println(
                    "3.4. BooleanQuery avanzada: MUST + MUST_NOT + SHOULD (ej: property_type MUST, price MUST_NOT, description SHOULD)");
            System.out.println("0. Volver al menú principal");
            System.out.print("Selecciona opción: ");

            String subOption = in.readLine();
            if (subOption == null || subOption.trim().isEmpty() || "0".equals(subOption.trim())) {
                break;
            }

            try {
                switch (subOption.trim()) {
                    case "3.1":
                    case "1":
                        ejecutarBooleanQueryCombinada(analyzer, similarity, in);
                        break;
                    case "3.2":
                    case "2":
                        ejecutarBooleanQueryConMustNot(analyzer, similarity, in);
                        break;
                    case "3.3":
                    case "3":
                        ejecutarBooleanQueryConShould(analyzer, similarity, in);
                        break;
                    case "3.4":
                    case "4":
                        ejecutarBooleanQueryAvanzada(analyzer, similarity, in);
                        break;
                    default:
                        System.out.println("Opción no válida.");
                        continue;
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * ⚠️ ADVERTENCIA IMPORTANTE SOBRE OPERADORES LÓGICOS EN BooleanQuery ⚠️
     * 
     * El profesor advierte: "tener cuidado con los operadores lógicos, mirar cada
     * una
     * con detalle ya que puede cambiar cosas depende de como se escriba"
     * 
     * CASOS PROBLEMÁTICOS QUE PUEDEN DAR RESULTADOS INESPERADOS:
     * 
     * 1. MUST_NOT SIN MUST/SHOULD (MÁS CRÍTICO):
     * - Si solo hay cláusulas MUST_NOT sin ninguna MUST o SHOULD, el comportamiento
     * puede ser impredecible:
     * * En algunas versiones de Lucene: devuelve TODOS los documentos
     * * En otras: devuelve NINGUNO
     * - Ejemplo problemático:
     * BooleanQuery con solo: property_type MUST_NOT "Entire home"
     * → Puede devolver todos los documentos o ninguno (comportamiento ambiguo)
     * - SOLUCIÓN: Siempre incluir al menos una cláusula MUST o SHOULD cuando uses
     * MUST_NOT
     * 
     * 2. SHOULD SIN MUST (COMPORTAMIENTO AMBIGUO):
     * - Si solo hay cláusulas SHOULD sin ninguna MUST:
     * * Por defecto: al menos UNA cláusula SHOULD debe cumplirse
     * * Pero esto puede cambiar según la configuración de minimumShouldMatch
     * - Ejemplo problemático:
     * BooleanQuery con solo: name SHOULD "beach" OR description SHOULD "pool"
     * → Puede devolver documentos que cumplan solo una condición, ambas, o ninguno
     * dependiendo de la configuración
     * - SOLUCIÓN: Si quieres comportamiento OR estricto, usa múltiples SHOULD
     * explícitos
     * 
     * 3. ORDEN DE LAS CLÁUSULAS (AFECTA SCORING):
     * - Aunque el orden NO debería afectar qué documentos se devuelven, SÍ afecta
     * el SCORE
     * - Ejemplo:
     * Query 1: (name:beach MUST) AND (description:pool MUST)
     * Query 2: (description:pool MUST) AND (name:beach MUST)
     * → Mismos documentos, pero scores diferentes
     * 
     * 4. INTERPRETACIÓN DE "AND" vs "OR" EN QUERIES TEXTUALES:
     * - Si parseas "beach AND pool" con QueryParser:
     * → Crea una BooleanQuery con ambas cláusulas como MUST
     * - Si parseas "beach OR pool":
     * → Crea una BooleanQuery con ambas cláusulas como SHOULD
     * - Si luego agregas esta query a otra BooleanQuery, el comportamiento puede
     * cambiar
     * - Ejemplo problemático:
     * QueryParser.parse("beach AND pool") → BooleanQuery con 2 MUST
     * Si luego haces: builder.add(esaQuery, BooleanClause.Occur.SHOULD)
     * → Ahora tienes un SHOULD que contiene 2 MUST internos (comportamiento
     * complejo)
     * - NOTA: En este proyecto, siempre se especifican los operadores
     * explícitamente
     * (MUST, MUST_NOT, SHOULD) al agregar queries, por lo que este caso NO aplica
     * en la mayoría de los métodos. Solo aplicaría si el usuario ingresa queries
     * complejas con AND/OR en QueryParser y luego se agregan a otra BooleanQuery.
     * 
     * 5. MUST_NOT CON QUERIES VACÍAS O SIN RESULTADOS:
     * - Si la query en MUST_NOT no encuentra nada, puede afectar el resultado final
     * - Ejemplo: property_type MUST_NOT "ValorQueNoExiste"
     * → La exclusión no tiene efecto, pero puede cambiar el scoring
     * 
     * REGLAS DE ORO:
     * - ✅ SIEMPRE incluir al menos un MUST o SHOULD cuando uses MUST_NOT
     * - ✅ Si solo usas SHOULD, entiende que requiere al menos uno por defecto
     * - ✅ Evita anidar BooleanQueries complejas dentro de otras
     * - ✅ Prueba tus queries con casos límite (valores que no existen, queries
     * vacías, etc.)
     */

    /**
     * 3.1: BooleanQuery combinando consulta numérica y textual
     * Ejemplo: review_scores_rating >= 4.7 AND amenity "pool"
     */
    private void ejecutarBooleanQueryCombinada(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 3.1: BooleanQuery Combinada ===");
        System.out.println("Ejemplo: review_scores_rating >= 4.7 AND amenity 'pool'");
        System.out.println();

        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // 1. Query numérica (similar a 2.1 pero para cualquier campo DoublePoint)
        System.out.println("--- Consulta Numérica ---");
        System.out.println("Campos disponibles: price, review_scores_rating");
        System.out.println("Formatos aceptados: >=4.7, >4.7, <4.7, <=4.7, =4.7, 4.7");
        System.out.print("Campo numérico: ");
        String campoNumerico = in.readLine();
        if (campoNumerico == null || campoNumerico.trim().isEmpty()) {
            System.out.println("Campo requerido. Cancelando.");
            return;
        }

        System.out.print("Operador y valor (ej: >=4.7): ");
        String valorStr = in.readLine();
        if (valorStr == null || valorStr.trim().isEmpty()) {
            System.out.println("Valor requerido. Cancelando.");
            return;
        }

        // Crear query numérica usando helper function
        Query queryNumerica = null;
        try {
            double valor = parseDoubleValue(valorStr.trim());
            String input = valorStr.trim();

            if (input.startsWith(">=")) {
                queryNumerica = DoublePoint.newRangeQuery(campoNumerico.trim(), valor, Double.MAX_VALUE);
            } else if (input.startsWith("<=")) {
                queryNumerica = DoublePoint.newRangeQuery(campoNumerico.trim(), 0.0, valor);
            } else if (input.startsWith(">")) {
                // Usamos un pequeño incremento para excluir el valor exacto
                double min = valor + 0.01;
                queryNumerica = DoublePoint.newRangeQuery(campoNumerico.trim(), min, Double.MAX_VALUE);
            } else if (input.startsWith("<")) {
                // Usamos un pequeño decremento para excluir el valor exacto
                double max = valor - 0.01;
                queryNumerica = DoublePoint.newRangeQuery(campoNumerico.trim(), 0.0, max);
            } else if (input.startsWith("=")) {
                queryNumerica = DoublePoint.newRangeQuery(campoNumerico.trim(), valor, valor);
            } else {
                // Sin operador explícito, asumir igualdad
                queryNumerica = DoublePoint.newRangeQuery(campoNumerico.trim(), valor, valor);
            }
            builder.add(queryNumerica, BooleanClause.Occur.MUST);
        } catch (NumberFormatException e) {
            System.out.println("Error: valor numérico inválido.");
            System.out.println("Ejemplos válidos: 4.7, =4.7, >4.7, >=4.7, <4.7, <=4.7");
            return;
        }

        // 2. Query textual (similar a 1.2)
        System.out.println();
        System.out.println("--- Consulta Textual ---");
        System.out.println("Campos disponibles: amenity, name, description, neighborhood_overview");
        System.out.print("Campo textual: ");
        String campoTexto = in.readLine();
        if (campoTexto == null || campoTexto.trim().isEmpty()) {
            System.out.println("Campo requerido. Cancelando.");
            return;
        }

        System.out.print("Valor a buscar (ej: pool): ");
        String valorTexto = in.readLine();
        if (valorTexto == null || valorTexto.trim().isEmpty()) {
            System.out.println("Valor requerido. Cancelando.");
            return;
        }

        // Crear query textual (lógica de 1.2)
        QueryParser parser = new QueryParser(campoTexto.trim(), analyzer);
        Query queryTexto = parser.parse(valorTexto.trim());
        builder.add(queryTexto, BooleanClause.Occur.MUST);

        // Ejecutar búsqueda combinada
        Query combinedQuery = builder.build();
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        TopDocs hits = searcher.search(combinedQuery, MAX_RESULTADOS_BUSQUEDA);
        mostrarResultados(searcher, hits);
        reader.close();

        System.out.println();
        System.out.println("=== Búsqueda Implementada ===");
        System.out.println("Query combinada:");
        System.out.println("  " + campoNumerico.trim() + " " + valorStr.trim() + " (MUST)");
        System.out.println("  " + campoTexto.trim() + ":" + valorTexto.trim() + " (MUST)");
        System.out.println("Índice: Properties");
    }

    /**
     * 3.2: BooleanQuery con MUST_NOT
     * Ejemplo: property_type MUST_NOT "Entire home" AND number_of_reviews = 0
     */
    private void ejecutarBooleanQueryConMustNot(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 3.2: BooleanQuery con MUST_NOT ===");
        System.out.println("Ejemplo: property_type MUST_NOT 'Entire home' AND number_of_reviews = 0");
        System.out.println();

        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // 1. Query textual con MUST_NOT (ej: property_type)
        System.out.println("--- Consulta Textual (MUST_NOT) ---");
        System.out.println("Campos disponibles: property_type, amenity, name, description, neighborhood_overview");
        System.out.print("Campo textual: ");
        String campoTexto = in.readLine();
        if (campoTexto == null || campoTexto.trim().isEmpty()) {
            System.out.println("Campo requerido. Cancelando.");
            return;
        }

        System.out.print("Valor a buscar (ej: Entire home): ");
        String valorTexto = in.readLine();
        if (valorTexto == null || valorTexto.trim().isEmpty()) {
            System.out.println("Valor requerido. Cancelando.");
            return;
        }

        // Crear query textual con MUST_NOT (excluir estos documentos)
        // ⚠️ IMPORTANTE: Este método SIEMPRE incluye un MUST después del MUST_NOT
        // para evitar el problema de "MUST_NOT sin MUST/SHOULD" que puede devolver
        // resultados inesperados (todos los documentos o ninguno)
        QueryParser parser = new QueryParser(campoTexto.trim(), analyzer);
        Query queryTexto = parser.parse(valorTexto.trim());
        builder.add(queryTexto, BooleanClause.Occur.MUST_NOT);

        // 2. Query numérica con MUST (incluir estos documentos)
        // ✅ Esta cláusula MUST es CRÍTICA: sin ella, el MUST_NOT anterior podría
        // causar comportamiento impredecible
        System.out.println();
        System.out.println("--- Consulta Numérica (MUST - debe cumplirse) ---");
        System.out.println("Campos disponibles: number_of_reviews, bedrooms, bathrooms");
        System.out.println("Formatos aceptados: >=0, >0, <0, <=0, =0, 0");
        System.out.print("Campo numérico: ");
        String campoNumerico = in.readLine();
        if (campoNumerico == null || campoNumerico.trim().isEmpty()) {
            System.out.println("Campo requerido. Cancelando.");
            return;
        }

        // Validar que el campo sea uno de los permitidos
        if (!campoNumerico.trim().equals("number_of_reviews") &&
                !campoNumerico.trim().equals("bedrooms") &&
                !campoNumerico.trim().equals("bathrooms")) {
            System.out.println("Error: campo no válido. Solo se permiten: number_of_reviews, bedrooms, bathrooms");
            return;
        }

        System.out.print("Operador y valor (ej: =0 para exacto): ");
        String valorStr = in.readLine();
        if (valorStr == null || valorStr.trim().isEmpty()) {
            System.out.println("Valor requerido. Cancelando.");
            return;
        }

        // Crear query numérica usando helper function (IntPoint)
        Query queryNumerica = null;
        try {
            String input = valorStr.trim();
            // Usar helper para parsear el valor (devuelve double)
            double valorDouble = parseDoubleValue(input);
            // Convertir a int
            int valor = (int) valorDouble;

            // Solo detectar operador para crear la query correcta
            if (input.startsWith(">=")) {
                queryNumerica = IntPoint.newRangeQuery(campoNumerico.trim(), valor, Integer.MAX_VALUE);
            } else if (input.startsWith("<=")) {
                queryNumerica = IntPoint.newRangeQuery(campoNumerico.trim(), Integer.MIN_VALUE, valor);
            } else if (input.startsWith(">")) {
                // Excluir el valor exacto
                int min = valor + 1;
                queryNumerica = IntPoint.newRangeQuery(campoNumerico.trim(), min, Integer.MAX_VALUE);
            } else if (input.startsWith("<")) {
                // Excluir el valor exacto
                int max = valor - 1;
                queryNumerica = IntPoint.newRangeQuery(campoNumerico.trim(), Integer.MIN_VALUE, max);
            } else if (input.startsWith("=")) {
                queryNumerica = IntPoint.newRangeQuery(campoNumerico.trim(), valor, valor);
            } else {
                // Sin operador explícito, asumir igualdad
                queryNumerica = IntPoint.newRangeQuery(campoNumerico.trim(), valor, valor);
            }
            builder.add(queryNumerica, BooleanClause.Occur.MUST);
        } catch (NumberFormatException e) {
            System.out.println("Error: valor numérico inválido.");
            System.out.println("Ejemplos válidos: 0, =0, >0, >=0, <0, <=0");
            return;
        }

        // Ejecutar búsqueda combinada
        Query combinedQuery = builder.build();
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        TopDocs hits = searcher.search(combinedQuery, MAX_RESULTADOS_BUSQUEDA);
        mostrarResultados(searcher, hits);
        reader.close();

        System.out.println();
        System.out.println("=== Búsqueda Implementada ===");
        System.out.println("Query combinada:");
        System.out.println("  " + campoTexto.trim() + ":" + valorTexto.trim() + " (MUST_NOT - excluir)");
        System.out.println("  " + campoNumerico.trim() + " " + valorStr.trim() + " (MUST - incluir)");
        System.out.println("Índice: Properties");
    }

    /**
     * 3.3: BooleanQuery con SHOULD
     * Ejemplo: neighbourhood_cleansed SHOULD "Hollywood" OR bedrooms SHOULD >= 3
     */
    private void ejecutarBooleanQueryConShould(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 3.3: BooleanQuery con SHOULD ===");
        System.out.println("Ejemplo: neighbourhood_cleansed 'Hollywood' OR bedrooms >= 3");
        System.out.println();

        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // 1. Query textual con SHOULD (ej: neighbourhood_cleansed)
        System.out.println("--- Consulta Textual (SHOULD) ---");
        System.out.println("Campos disponibles: neighbourhood_cleansed, property_type, amenity, name, description");
        System.out.print("Campo textual: ");
        String campoTexto = in.readLine();
        if (campoTexto == null || campoTexto.trim().isEmpty()) {
            System.out.println("Campo requerido. Cancelando.");
            return;
        }

        System.out.print("Valor a buscar (ej: Hollywood): ");
        String valorTexto = in.readLine();
        if (valorTexto == null || valorTexto.trim().isEmpty()) {
            System.out.println("Valor requerido. Cancelando.");
            return;
        }

        // Crear query textual con SHOULD fijo
        // ⚠️ IMPORTANTE: Cuando solo hay cláusulas SHOULD (sin MUST), Lucene requiere
        // que al menos UNA se cumpla por defecto. Si ambas se cumplen, el score es
        // mayor.
        // Si ninguna se cumple, no se devuelve el documento.
        QueryParser parser = new QueryParser(campoTexto.trim(), analyzer);
        Query queryTexto = parser.parse(valorTexto.trim());
        builder.add(queryTexto, BooleanClause.Occur.SHOULD);

        // 2. Query numérica con SHOULD (ej: bedrooms)
        // ⚠️ Comportamiento: Este método usa 2 SHOULD, por lo que devolverá documentos
        // que cumplan al menos una condición (OR lógico), con mayor score si cumplen
        // ambas
        System.out.println();
        System.out.println("--- Consulta Numérica (SHOULD) ---");
        System.out.println("Campos disponibles: number_of_reviews, bedrooms, bathrooms");
        System.out.println("Formatos aceptados: >=3, >3, <3, <=3, =3, 3");
        System.out.print("Campo numérico: ");
        String campoNumerico = in.readLine();
        if (campoNumerico == null || campoNumerico.trim().isEmpty()) {
            System.out.println("Campo requerido. Cancelando.");
            return;
        }

        // Validar que el campo sea uno de los permitidos
        if (!campoNumerico.trim().equals("number_of_reviews") &&
                !campoNumerico.trim().equals("bedrooms") &&
                !campoNumerico.trim().equals("bathrooms")) {
            System.out.println("Error: campo no válido. Solo se permiten: number_of_reviews, bedrooms, bathrooms");
            return;
        }

        System.out.print("Operador y valor (ej: >=3): ");
        String valorStr = in.readLine();
        if (valorStr == null || valorStr.trim().isEmpty()) {
            System.out.println("Valor requerido. Cancelando.");
            return;
        }

        // Crear query numérica usando helper function (IntPoint)
        Query queryNumerica = null;
        try {
            String input = valorStr.trim();
            // Usar helper para parsear el valor (devuelve double)
            double valorDouble = parseDoubleValue(input);
            // Convertir a int
            int valor = (int) valorDouble;

            // Solo detectar operador para crear la query correcta
            if (input.startsWith(">=")) {
                queryNumerica = IntPoint.newRangeQuery(campoNumerico.trim(), valor, Integer.MAX_VALUE);
            } else if (input.startsWith("<=")) {
                queryNumerica = IntPoint.newRangeQuery(campoNumerico.trim(), Integer.MIN_VALUE, valor);
            } else if (input.startsWith(">")) {
                // Excluir el valor exacto
                int min = valor + 1;
                queryNumerica = IntPoint.newRangeQuery(campoNumerico.trim(), min, Integer.MAX_VALUE);
            } else if (input.startsWith("<")) {
                // Excluir el valor exacto
                int max = valor - 1;
                queryNumerica = IntPoint.newRangeQuery(campoNumerico.trim(), Integer.MIN_VALUE, max);
            } else if (input.startsWith("=")) {
                queryNumerica = IntPoint.newRangeQuery(campoNumerico.trim(), valor, valor);
            } else {
                // Sin operador explícito, asumir igualdad
                queryNumerica = IntPoint.newRangeQuery(campoNumerico.trim(), valor, valor);
            }
            builder.add(queryNumerica, BooleanClause.Occur.SHOULD);
        } catch (NumberFormatException e) {
            System.out.println("Error: valor numérico inválido.");
            System.out.println("Ejemplos válidos: 3, =3, >3, >=3, <3, <=3");
            return;
        }

        // Ejecutar búsqueda combinada
        Query combinedQuery = builder.build();
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        TopDocs hits = searcher.search(combinedQuery, MAX_RESULTADOS_BUSQUEDA);
        mostrarResultados(searcher, hits);
        reader.close();

        System.out.println();
        System.out.println("=== Búsqueda Implementada ===");
        System.out.println("Query combinada:");
        System.out.println("  " + campoTexto.trim() + ":" + valorTexto.trim() + " (SHOULD)");
        System.out.println("  " + campoNumerico.trim() + " " + valorStr.trim() + " (SHOULD)");
        System.out.println("Índice: Properties");
    }

    /**
     * 3.4: BooleanQuery avanzada con MUST + MUST_NOT + SHOULD
     * Ejemplo: property_type MUST "Entire guesthouse", price MUST_NOT >200,
     * description SHOULD "terrace"
     */
    private void ejecutarBooleanQueryAvanzada(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 3.4: BooleanQuery Avanzada ===");
        System.out.println(
                "Ejemplo: property_type MUST 'Entire guesthouse', price MUST_NOT >200, description SHOULD 'terrace'");
        System.out.println();

        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // 1. Query textual con MUST (ej: property_type)
        System.out.println("--- Consulta Textual (MUST) ---");
        System.out.println("Campos disponibles: property_type, neighbourhood_cleansed, amenity, name");
        System.out.print("Campo textual: ");
        String campoTextoMust = in.readLine();
        if (campoTextoMust == null || campoTextoMust.trim().isEmpty()) {
            System.out.println("Campo requerido. Cancelando.");
            return;
        }

        System.out.print("Valor a buscar (ej: Entire guesthouse): ");
        String valorTextoMust = in.readLine();
        if (valorTextoMust == null || valorTextoMust.trim().isEmpty()) {
            System.out.println("Valor requerido. Cancelando.");
            return;
        }

        // Crear query textual con MUST
        QueryParser parserMust = new QueryParser(campoTextoMust.trim(), analyzer);
        Query queryTextoMust = parserMust.parse(valorTextoMust.trim());
        builder.add(queryTextoMust, BooleanClause.Occur.MUST);

        // 2. Query numérica con MUST_NOT (ej: price - DoublePoint)
        System.out.println();
        System.out.println("--- Consulta Numérica (MUST_NOT) ---");
        System.out.println("Campos disponibles: price, review_scores_rating");
        System.out.println("Formatos aceptados: >=200, >200, <200, <=200, =200, 200");
        System.out.print("Campo numérico: ");
        String campoNumericoMustNot = in.readLine();
        if (campoNumericoMustNot == null || campoNumericoMustNot.trim().isEmpty()) {
            System.out.println("Campo requerido. Cancelando.");
            return;
        }

        // Validar que el campo sea DoublePoint
        if (!campoNumericoMustNot.trim().equals("price") &&
                !campoNumericoMustNot.trim().equals("review_scores_rating")) {
            System.out.println("Error: campo no válido. Solo se permiten: price, review_scores_rating");
            return;
        }

        System.out.print("Operador y valor (ej: >200): ");
        String valorStrMustNot = in.readLine();
        if (valorStrMustNot == null || valorStrMustNot.trim().isEmpty()) {
            System.out.println("Valor requerido. Cancelando.");
            return;
        }

        // Crear query numérica con MUST_NOT usando helper function (DoublePoint)
        Query queryNumericaMustNot = null;
        try {
            String input = valorStrMustNot.trim();
            double valor = parseDoubleValue(input);

            // Solo detectar operador para crear la query correcta
            if (input.startsWith(">=")) {
                queryNumericaMustNot = DoublePoint.newRangeQuery(campoNumericoMustNot.trim(), valor, Double.MAX_VALUE);
            } else if (input.startsWith("<=")) {
                queryNumericaMustNot = DoublePoint.newRangeQuery(campoNumericoMustNot.trim(), 0.0, valor);
            } else if (input.startsWith(">")) {
                // Excluir el valor exacto
                double min = valor + 0.01;
                queryNumericaMustNot = DoublePoint.newRangeQuery(campoNumericoMustNot.trim(), min, Double.MAX_VALUE);
            } else if (input.startsWith("<")) {
                // Excluir el valor exacto
                double max = valor - 0.01;
                queryNumericaMustNot = DoublePoint.newRangeQuery(campoNumericoMustNot.trim(), 0.0, max);
            } else if (input.startsWith("=")) {
                queryNumericaMustNot = DoublePoint.newRangeQuery(campoNumericoMustNot.trim(), valor, valor);
            } else {
                // Sin operador explícito, asumir igualdad
                queryNumericaMustNot = DoublePoint.newRangeQuery(campoNumericoMustNot.trim(), valor, valor);
            }
            builder.add(queryNumericaMustNot, BooleanClause.Occur.MUST_NOT);
        } catch (NumberFormatException e) {
            System.out.println("Error: valor numérico inválido.");
            System.out.println("Ejemplos válidos: 200, =200, >200, >=200, <200, <=200");
            return;
        }

        // 3. Query textual con SHOULD (ej: description)
        System.out.println();
        System.out.println("--- Consulta Textual (SHOULD) ---");
        System.out.println("Campos disponibles: description, neighborhood_overview, name");
        System.out.print("Campo textual: ");
        String campoTextoShould = in.readLine();
        if (campoTextoShould == null || campoTextoShould.trim().isEmpty()) {
            System.out.println("Campo requerido. Cancelando.");
            return;
        }

        System.out.print("Valor a buscar (ej: terrace): ");
        String valorTextoShould = in.readLine();
        if (valorTextoShould == null || valorTextoShould.trim().isEmpty()) {
            System.out.println("Valor requerido. Cancelando.");
            return;
        }

        // Crear query textual con SHOULD
        QueryParser parserShould = new QueryParser(campoTextoShould.trim(), analyzer);
        Query queryTextoShould = parserShould.parse(valorTextoShould.trim());
        builder.add(queryTextoShould, BooleanClause.Occur.SHOULD);

        // Ejecutar búsqueda combinada
        Query combinedQuery = builder.build();
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        TopDocs hits = searcher.search(combinedQuery, MAX_RESULTADOS_BUSQUEDA);
        mostrarResultados(searcher, hits);
        reader.close();

        System.out.println();
        System.out.println("=== Búsqueda Implementada ===");
        System.out.println("Query combinada:");
        System.out.println("  " + campoTextoMust.trim() + ":" + valorTextoMust.trim() + " (MUST)");
        System.out.println("  " + campoNumericoMustNot.trim() + " " + valorStrMustNot.trim() + " (MUST_NOT)");
        System.out.println("  " + campoTextoShould.trim() + ":" + valorTextoShould.trim() + " (SHOULD)");
        System.out.println("Índice: Properties");
    }

    /**
     * Helper: Parsea un string numérico con operador y devuelve el valor double
     * Soporta formatos: ">30.3", "<10", "10", "=100", ">=50.5", "<=20"
     * 
     * @param valorStr String con formato de operador y valor
     * @return El valor numérico extraído como double
     * @throws NumberFormatException Si el formato es inválido
     */
    private double parseDoubleValue(String valorStr) throws NumberFormatException {
        String input = valorStr.trim();

        if (input.startsWith(">=")) {
            return Double.parseDouble(input.substring(2).trim());
        } else if (input.startsWith("<=")) {
            return Double.parseDouble(input.substring(2).trim());
        } else if (input.startsWith(">")) {
            return Double.parseDouble(input.substring(1).trim());
        } else if (input.startsWith("<")) {
            return Double.parseDouble(input.substring(1).trim());
        } else if (input.startsWith("=")) {
            return Double.parseDouble(input.substring(1).trim());
        } else {
            // Sin operador explícito, asumir que es solo el número
            return Double.parseDouble(input);
        }
    }

    /**
     * Helper: Convierte una fecha en formato YYYY-MM-DD a epoch millis
     * Acepta: "2008-07-11"
     */
    private long parseDate(String input) throws DateTimeParseException {
        String trimmed = input.trim();
        LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * Helper: Formatea un timestamp (epoch millis) a fecha legible YYYY-MM-DD
     */
    private String formatTimestamp(long timestamp) {
        try {
            Instant instant = Instant.ofEpochMilli(timestamp);
            LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return String.valueOf(timestamp);
        }
    }

    /**
     * Helper: Parsea el tipo de cláusula Boolean
     */
    private BooleanClause.Occur parseOccur(String tipo, BooleanClause.Occur defaultOccur) {
        if (tipo == null || tipo.trim().isEmpty()) {
            return defaultOccur;
        }
        switch (tipo.trim().toUpperCase()) {
            case "MUST":
                return BooleanClause.Occur.MUST;
            case "SHOULD":
                return BooleanClause.Occur.SHOULD;
            case "MUST_NOT":
                return BooleanClause.Occur.MUST_NOT;
            default:
                return defaultOccur;
        }
    }

    /**
     * Menú para consultas ordenadas por criterio distinto al score
     */
    private void menuQueriesOrdenadas(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException {
        while (true) {
            System.out.println("\n=== 4. CONSULTAS ORDENADAS ===");
            System.out.println("4.1. Ordenar por 'number_of_reviews' (descendente) en Properties");
            System.out.println("4.2. Ordenar por 'host_since' (más antiguo primero) en Hosts");
            System.out.println("0. Volver al menú principal");
            System.out.print("Selecciona opción: ");

            String subOption = in.readLine();
            if (subOption == null || subOption.trim().isEmpty() || "0".equals(subOption.trim())) {
                break;
            }

            try {
                switch (subOption.trim()) {
                    case "4.1":
                    case "1":
                        ejecutarQueryOrdenadaPorReviews(analyzer, similarity, in);
                        break;
                    case "4.2":
                    case "2":
                        ejecutarQueryOrdenadaPorHostSince(analyzer, similarity, in);
                        break;
                    default:
                        System.out.println("Opción no válida.");
                        continue;
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 4.1: Consulta ordenada por 'number_of_reviews' (descendente) en Properties
     */
    private void ejecutarQueryOrdenadaPorReviews(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 4.1: Ordenar por 'number_of_reviews' DESC (Properties) ===");
        System.out.println("Nota: Si ingresa una consulta, se buscará en el campo 'description' por defecto.");
        System.out.println("      Si deja la consulta vacía (Enter), se devolverán todos los documentos.");
        System.out.print("Ingrese la consulta textual (o Enter para buscar todos): ");
        String consulta = in.readLine();

        Query query = null;
        if (consulta != null && !consulta.trim().isEmpty()) {
            // Parsear la consulta si se proporciona
            QueryParser parser = new QueryParser("description", analyzer);
            query = parser.parse(consulta.trim());
        } else {
            // Que devuelva todos los documentos
            query = new MatchAllDocsQuery();
        }

        // Logica de búsqueda con Sort
        Sort sort = new Sort(new SortField("number_of_reviews", SortField.Type.INT, true)); // true = descendente
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        // doDocScores=true para calcular scores incluso cuando se ordena por un campo
        TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA, sort, true);
        mostrarResultados(searcher, hits);
        reader.close();

        System.out.println("\n=== CONFIGURACIÓN DE BÚSQUEDA ===");
        System.out.println("Índice: Properties");
        System.out.println("Ordenamiento: number_of_reviews (descendente - más reviews primero)");
        if (consulta != null && !consulta.trim().isEmpty()) {
            System.out.println("Campo de búsqueda: description");
            System.out.println("Consulta: " + consulta.trim());
        } else {
            System.out.println("Consulta: Todos los documentos (sin filtro)");
        }
    }

    /**
     * 4.2: Consulta ordenada por 'host_since' (más antiguo primero) en Hosts
     */
    private void ejecutarQueryOrdenadaPorHostSince(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 4.2: Ordenar por 'host_since' ASC (Hosts) ===");
        System.out.println("Nota: Si ingresa una consulta, se buscará en el campo 'host_name' por defecto.");
        System.out.println("      Si deja la consulta vacía (Enter), se devolverán todos los documentos.");
        System.out.print("Ingrese la consulta textual (o Enter para buscar todos): ");
        String consulta = in.readLine();

        Query query = null;
        if (consulta != null && !consulta.trim().isEmpty()) {
            // Parsear la consulta si se proporciona
            QueryParser parser = new QueryParser("host_name", analyzer);
            query = parser.parse(consulta.trim());
        } else {
            // Crear query que devuelva todos los documentos
            query = new MatchAllDocsQuery();
        }

        // Lógica de búsqueda con Sort
        Sort sort = new Sort(new SortField("host_since", SortField.Type.LONG, false)); // false = ascendente (más
                                                                                       // antiguo primero)
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathHosts)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        // doDocScores=true para calcular scores incluso cuando se ordena por un campo
        TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA, sort, true);
        mostrarResultadosHosts(searcher, hits);
        reader.close();

        System.out.println("\n=== CONFIGURACIÓN DE BÚSQUEDA ===");
        System.out.println("Índice: Hosts");
        System.out.println("Ordenamiento: host_since (ascendente - más antiguo primero)");
        if (consulta != null && !consulta.trim().isEmpty()) {
            System.out.println("Campo de búsqueda: host_name");
            System.out.println("Consulta: " + consulta.trim());
        } else {
            System.out.println("Consulta: Todos los documentos (sin filtro)");
        }
    }

    /**
     * Menú para consultas geográficas
     */
    private void menuQueriesGeograficas(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException {
        while (true) {
            System.out.println("\n=== 5. CONSULTAS GEOGRÁFICAS ===");
            System.out.println("5.1. Búsqueda por distancia (lat, lon, radio en metros)");
            System.out.println("5.2. Ordenar por distancia a un punto (lat, lon)");
            System.out.println("0. Volver al menú principal");
            System.out.print("Selecciona opción: ");

            String subOption = in.readLine();
            if (subOption == null || subOption.trim().isEmpty() || "0".equals(subOption.trim())) {
                break;
            }

            try {
                switch (subOption.trim()) {
                    case "5.1":
                    case "1":
                        ejecutarQueryGeograficaDistancia(analyzer, similarity, in);
                        break;
                    case "5.2":
                    case "2":
                        ejecutarQueryOrdenadaPorDistancia(analyzer, similarity, in);
                        break;
                    default:
                        System.out.println("Opción no válida.");
                        continue;
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 5.1: Búsqueda geográfica por distancia
     * Usa LatLonPoint.newDistanceQuery para buscar propiedades dentro de un radio
     */
    private void ejecutarQueryGeograficaDistancia(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException {
        System.out.println("\n=== 5.1: Búsqueda Geográfica por Distancia ===");
        System.out.println("Busca propiedades dentro de un radio desde un punto (lat, lon)");
        System.out.println();
        System.out.println("Rangos típicos para Los Angeles:");
        System.out.println("  Latitude: 33.339 a 34.811 (media: 34.055)");
        System.out.println("  Longitude: -118.917 a -117.654 (media: -118.31)");
        System.out.println();

        // Solicitar latitud
        System.out.print("Latitud (ej: 34.0522 para Los Angeles): ");
        String latStr = in.readLine();
        if (latStr == null || latStr.trim().isEmpty()) {
            System.out.println("Latitud requerida. Cancelando.");
            return;
        }

        // Solicitar longitud
        System.out.print("Longitud (ej: -118.2437 para Los Angeles): ");
        String lonStr = in.readLine();
        if (lonStr == null || lonStr.trim().isEmpty()) {
            System.out.println("Longitud requerida. Cancelando.");
            return;
        }

        // Solicitar radio en metros
        System.out.print("Radio en metros (ej: 5000 para 5km, 1000 para 1km): ");
        String radioStr = in.readLine();
        if (radioStr == null || radioStr.trim().isEmpty()) {
            System.out.println("Radio requerido. Cancelando.");
            return;
        }

        try {
            double lat = Double.parseDouble(latStr.trim());
            double lon = Double.parseDouble(lonStr.trim());
            double radioMetros = Double.parseDouble(radioStr.trim());

            // Validar rangos razonables
            if (lat < 33.0 || lat > 35.0) {
                System.out.println("Advertencia: latitud fuera del rango típico de Los Angeles (33.339 - 34.811)");
            }
            if (lon > -117.0 || lon < -119.0) {
                System.out.println("Advertencia: longitud fuera del rango típico de Los Angeles (-118.917 - -117.654)");
            }
            if (radioMetros <= 0) {
                System.out.println("Error: el radio debe ser mayor que 0.");
                return;
            }

            // Crear query geográfica usando LatLonPoint.newDistanceQuery
            // El campo en el índice se llama "location"
            Query query = LatLonPoint.newDistanceQuery("location", lat, lon, radioMetros);

            // Ejecutar búsqueda
            IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(similarity);
            TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
            mostrarResultados(searcher, hits);
            reader.close();

            System.out.println();
            System.out.println("=== Búsqueda Implementada ===");
            System.out.println("Ubicación: (" + String.format("%.6f", lat) + ", " + String.format("%.6f", lon) + ")");
            System.out.println("Radio: " + String.format("%.0f", radioMetros) + " metros (" +
                    String.format("%.2f", radioMetros / 1000.0) + " km)");
            System.out.println("Índice: Properties");
            System.out.println("Campo: location (LatLonPoint)");
        } catch (NumberFormatException e) {
            System.out.println("Error: valores numéricos inválidos.");
            System.out.println("Asegúrese de ingresar números válidos para latitud, longitud y radio.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 5.2: Consulta ordenada por distancia a un punto geográfico
     * Similar a 4.1 pero prioriza resultados por distancia geográfica
     * 
     * IMPORTANTE: newDistanceFeatureQuery NO filtra documentos, solo ajusta el
     * score.
     * Fórmula del score: score = weight × (pivotDistanceMeters /
     * (pivotDistanceMeters + distance))
     * - Si el documento está en el origen: score = weight
     * - Si está a distancia pivotDistanceMeters: score = weight/2
     * - Si está más lejos: score decae suavemente hacia cero
     */
    private void ejecutarQueryOrdenadaPorDistancia(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 5.2: Priorizar por Distancia a un Punto (Properties) ===");
        System.out.println("Nota: Esta query NO filtra por distancia, solo ajusta el score.");
        System.out.println("      Los documentos más cercanos al punto tendrán mayor relevancia.");
        System.out.println("      Si ingresa una consulta, se buscará en el campo 'description' por defecto.");
        System.out.println("      Si deja la consulta vacía (Enter), se devolverán todos los documentos.");
        System.out.println();
        System.out.println("Rangos típicos para Los Angeles:");
        System.out.println("  Latitude: 33.339 a 34.811 (media: 34.055)");
        System.out.println("  Longitude: -118.917 a -117.654 (media: -118.31)");
        System.out.println();

        // Solicitar consulta opcional
        System.out.print("Ingrese la consulta textual (o Enter para buscar todos): ");
        String consulta = in.readLine();

        Query query = null;
        if (consulta != null && !consulta.trim().isEmpty()) {
            // Parsear la consulta si se proporciona
            QueryParser parser = new QueryParser("description", analyzer);
            query = parser.parse(consulta.trim());
        } else {
            // Crear query que devuelva todos los documentos
            query = new MatchAllDocsQuery();
        }

        // Solicitar punto de referencia para ordenar por distancia
        System.out.println();
        System.out.print("Latitud del punto de referencia (ej: 34.0522 para Los Angeles): ");
        String latStr = in.readLine();
        if (latStr == null || latStr.trim().isEmpty()) {
            System.out.println("Latitud requerida. Cancelando.");
            return;
        }

        System.out.print("Longitud del punto de referencia (ej: -118.2437 para Los Angeles): ");
        String lonStr = in.readLine();
        if (lonStr == null || lonStr.trim().isEmpty()) {
            System.out.println("Longitud requerida. Cancelando.");
            return;
        }

        try {
            double lat = Double.parseDouble(latStr.trim());
            double lon = Double.parseDouble(lonStr.trim());

            // Validar rangos razonables
            if (lat < 33.0 || lat > 35.0) {
                System.out.println("Advertencia: latitud fuera del rango típico de Los Angeles (33.339 - 34.811)");
            }
            if (lon > -117.0 || lon < -119.0) {
                System.out.println("Advertencia: longitud fuera del rango típico de Los Angeles (-118.917 - -117.654)");
            }

            // Solicitar parámetros para newDistanceFeatureQuery
            System.out.print("Peso (weight) para la query de distancia [default: 1.0]: ");
            String weightStr = in.readLine();
            float weight = 1.0f;
            if (weightStr != null && !weightStr.trim().isEmpty()) {
                try {
                    weight = Float.parseFloat(weightStr.trim());
                } catch (NumberFormatException e) {
                    System.out.println("Advertencia: peso inválido, usando 1.0 por defecto");
                    weight = 1.0f;
                }
            }

            System.out.print("Distancia pivote en metros (pivotDistanceMeters) [default: 1000]: ");
            String pivotStr = in.readLine();
            double pivotDistanceMeters = 1000.0;
            if (pivotStr != null && !pivotStr.trim().isEmpty()) {
                try {
                    pivotDistanceMeters = Double.parseDouble(pivotStr.trim());
                } catch (NumberFormatException e) {
                    System.out.println("Advertencia: distancia pivote inválida, usando 1000 por defecto");
                    pivotDistanceMeters = 1000.0;
                }
            }

            // Crear query de distancia usando LatLonPoint.newDistanceFeatureQuery
            // IMPORTANTE: Esta query NO filtra documentos, solo ajusta el score según
            // distancia
            // El campo en el índice se llama "location"
            Query distanceFeatureQuery = LatLonPoint.newDistanceFeatureQuery(
                    "location", weight, lat, lon, pivotDistanceMeters);

            // Combinar la query original con la query de distancia usando BooleanQuery
            // - query (MUST): filtra los documentos que deben coincidir
            // - distanceFeatureQuery (SHOULD): ajusta el score según distancia (no filtra)
            // Esto prioriza documentos más cercanos sin excluir los lejanos
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(query, BooleanClause.Occur.MUST);
            builder.add(distanceFeatureQuery, BooleanClause.Occur.SHOULD);
            Query combinedQuery = builder.build();

            // Ejecutar búsqueda
            // Los resultados se ordenan por score (más cercanos = mayor score)
            IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(similarity);
            TopDocs hits = searcher.search(combinedQuery, MAX_RESULTADOS_BUSQUEDA);
            mostrarResultados(searcher, hits);
            reader.close();

            System.out.println("\n=== CONFIGURACIÓN DE BÚSQUEDA ===");
            System.out.println("Índice: Properties");
            System.out.println("Priorización: por distancia a (" + String.format("%.6f", lat) + ", " +
                    String.format("%.6f", lon) + ") - más cercano = mayor score");
            System.out.println("Peso (weight): " + weight + " (score máximo cuando distancia = 0)");
            System.out.println("Distancia pivote: " + String.format("%.0f", pivotDistanceMeters) +
                    " metros (score = weight/2 a esta distancia)");
            System.out.println("Fórmula del score: weight × (pivotDistanceMeters / (pivotDistanceMeters + distance))");
            if (consulta != null && !consulta.trim().isEmpty()) {
                System.out.println("Campo de búsqueda: description");
                System.out.println("Consulta: " + consulta.trim());
            } else {
                System.out.println("Consulta: Todos los documentos (sin filtro)");
            }
            System.out.println("Query usada: newDistanceFeatureQuery (SHOULD) + query original (MUST)");
            System.out.println("Nota: newDistanceFeatureQuery NO filtra, solo ajusta el score por distancia");
        } catch (NumberFormatException e) {
            System.out.println("Error: valores numéricos inválidos.");
            System.out.println("Asegúrese de ingresar números válidos para latitud y longitud.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Menú para consultas que involucren dos índices simultáneamente
     */
    private void menuQueriesMultiIndice(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException {
        while (true) {
            System.out.println("\n=== 6. CONSULTAS MULTI-ÍNDICE ===");
            System.out.println("6.1. Búsqueda textual en ambos índices (Properties + Hosts)");
            System.out.println("0. Volver al menú principal");
            System.out.print("Selecciona opción: ");

            String subOption = in.readLine();
            if (subOption == null || subOption.trim().isEmpty() || "0".equals(subOption.trim())) {
                break;
            }

            try {
                switch (subOption.trim()) {
                    case "6.1":
                    case "1":
                        ejecutarQueryMultiIndiceTexto(analyzer, similarity, in);
                        break;
                    default:
                        System.out.println("Opción no válida.");
                        continue;
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 6.1: Búsqueda textual en ambos índices (Properties + Hosts)
     * Busca en campos de texto de ambos índices simultáneamente
     */
    private void ejecutarQueryMultiIndiceTexto(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 6.1: Búsqueda textual en ambos índices ===");
        System.out.println("Busca en: name, description, neighborhood_overview (Properties)");
        System.out.println("          host_name, host_about (Hosts)");
        System.out.print("Ingrese la consulta textual (ej: 'beach', 'yoga'): ");
        String valor = in.readLine();

        if (valor == null || valor.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        IndexReader readerProperties = null;
        IndexReader readerHosts = null;
        IndexReader multiReader = null;

        try {
            // Abrir ambos índices
            readerProperties = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
            readerHosts = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathHosts)));

            // Combinar con MultiReader
            multiReader = new MultiReader(readerProperties, readerHosts);
            IndexSearcher searcher = new IndexSearcher(multiReader);
            searcher.setSimilarity(similarity);

            // Crear query multi-campo que busca en ambos índices
            String[] campos = { "name", "description", "neighborhood_overview", "host_name", "host_about" };
            MultiFieldQueryParser parser = new MultiFieldQueryParser(campos, analyzer);
            Query query = parser.parse(valor.trim());

            TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
            mostrarResultadosMultiIndice(searcher, hits, readerProperties.maxDoc());

            System.out.println("Búsqueda implementada para: " + valor.trim());
            System.out.println("Índices: Properties + Hosts (combinados con MultiReader)");
            System.out.println("Campos buscados: name, description, neighborhood_overview, host_name, host_about");
        } finally {
            if (multiReader != null) {
                multiReader.close();
            } else {
                // Si multiReader falló, cerrar los readers individuales
                if (readerProperties != null)
                    readerProperties.close();
                if (readerHosts != null)
                    readerHosts.close();
            }
        }
    }

    /**
     * Muestra los resultados de una búsqueda multi-índice
     * Detecta automáticamente si el documento viene del índice de Properties o
     * Hosts
     * basándose en el doc ID (si es menor que numDocsProperties, viene de
     * Properties)
     */
    private void mostrarResultadosMultiIndice(IndexSearcher searcher, TopDocs hits, int numDocsProperties)
            throws IOException {
        // Obtener el total de resultados usando reflexión para acceder al campo value
        long totalCoincidencias;
        try {
            java.lang.reflect.Field valueField = hits.totalHits.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            totalCoincidencias = valueField.getLong(hits.totalHits);
        } catch (Exception e) {
            // Fallback: usar el número de resultados mostrados
            totalCoincidencias = hits.scoreDocs.length;
        }
        int resultadosMostrados = hits.scoreDocs.length;
        System.out.println("\n=== RESULTADOS (MULTI-ÍNDICE) ===");
        System.out.println("Total de coincidencias: " + totalCoincidencias);
        System.out.println("Mostrando: " + resultadosMostrados + " de " + totalCoincidencias +
                " (limitado por MAX_RESULTADOS_BUSQUEDA = " + MAX_RESULTADOS_BUSQUEDA + ")");
        System.out.println();

        int resultadoNum = 1;
        for (ScoreDoc hit : hits.scoreDocs) {
            Document doc = searcher.storedFields().document(hit.doc);

            // Determinar de qué índice viene el documento
            boolean esProperties = hit.doc < numDocsProperties;
            String tipoIndice = esProperties ? "Properties" : "Hosts";

            System.out.println("------------------------------------");
            System.out.println("RESULTADO " + resultadoNum + " de " + resultadosMostrados +
                    " (de " + totalCoincidencias + " coincidencias totales)");
            System.out.println("Índice: " + tipoIndice);
            System.out.println("Doc ID (Lucene): " + hit.doc);

            if (esProperties) {
                // Mostrar campos de Properties
                String name = doc.get("name");
                String description = doc.get("description");
                String propertyType = doc.get("property_type");
                String neighbourhood = doc.get("neighbourhood_cleansed");
                String listingUrl = doc.get("listing_url");
                String hostId = doc.get("host_id");

                if (listingUrl != null) {
                    System.out.println("URL (listing_url): " + listingUrl);
                }
                if (name != null) {
                    System.out.println("Nombre (name): " + name);
                }
                if (propertyType != null) {
                    System.out.println("Tipo (property_type): " + propertyType);
                }
                if (neighbourhood != null) {
                    System.out.println("Barrio (neighbourhood_cleansed): " + neighbourhood);
                }
                if (hostId != null) {
                    System.out.println("Host ID (host_id): " + hostId);
                }

                // Campos numéricos
                try {
                    if (doc.getField("price") != null) {
                        Double price = doc.getField("price").numericValue().doubleValue();
                        System.out.println("Precio (price): $" + String.format("%.2f", price));
                    }
                    if (doc.getField("review_scores_rating") != null) {
                        Double rating = doc.getField("review_scores_rating").numericValue().doubleValue();
                        System.out.println("Rating (review_scores_rating): " + String.format("%.1f", rating));
                    }
                } catch (Exception e) {
                    // Ignorar si el campo no está disponible
                }

                if (description != null && description.length() > 0) {
                    String descShort = description.length() > 200 ? description.substring(0, 200) + "..." : description;
                    System.out.println("Descripción (description): " + descShort);
                }
            } else {
                // Mostrar campos de Hosts
                String hostName = doc.get("host_name");
                String hostLocation = doc.get("host_location");
                String hostNeighbourhood = doc.get("host_neighbourhood");
                String hostAbout = doc.get("host_about");
                String hostId = doc.get("host_id");
                String hostUrl = doc.get("host_url");

                if (hostId != null) {
                    System.out.println("Host ID (host_id): " + hostId);
                }
                if (hostUrl != null) {
                    System.out.println("URL (host_url): " + hostUrl);
                }
                if (hostName != null) {
                    System.out.println("Nombre (host_name): " + hostName);
                }
                if (hostLocation != null) {
                    System.out.println("Ubicación (host_location): " + hostLocation);
                }
                if (hostNeighbourhood != null) {
                    System.out.println("Barrio (host_neighbourhood): " + hostNeighbourhood);
                }

                // Campos numéricos
                try {
                    if (doc.getField("host_is_superhost") != null) {
                        Integer superhost = doc.getField("host_is_superhost").numericValue().intValue();
                        System.out.println("Superhost (host_is_superhost): " + (superhost == 1 ? "Sí" : "No"));
                    }
                } catch (Exception e) {
                    // Ignorar si el campo no está disponible
                }

                if (hostAbout != null && hostAbout.length() > 0) {
                    String aboutShort = hostAbout.length() > 200 ? hostAbout.substring(0, 200) + "..." : hostAbout;
                    System.out.println("Acerca de (host_about): " + aboutShort);
                }
            }

            System.out.println("Score: " + hit.score);
            System.out.println();
            resultadoNum++;
        }
    }

    /**
     * Query textual en múltiples campos
     * Ejemplo: "beach" busca en name, description y neighborhood_overview
     */
    /**
     * Muestra los resultados de una búsqueda en el índice de propiedades
     */
    private void mostrarResultados(IndexSearcher searcher, TopDocs hits) throws IOException {
        // Obtener el total de resultados usando reflexión para acceder al campo value
        long totalCoincidencias;
        try {
            java.lang.reflect.Field valueField = hits.totalHits.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            totalCoincidencias = valueField.getLong(hits.totalHits);
        } catch (Exception e) {
            // Fallback: usar el número de resultados mostrados
            totalCoincidencias = hits.scoreDocs.length;
        }
        int resultadosMostrados = hits.scoreDocs.length;
        System.out.println("\n=== RESULTADOS ===");
        System.out.println("Total de coincidencias: " + totalCoincidencias);
        System.out.println("Mostrando: " + resultadosMostrados + " de " + totalCoincidencias +
                " (limitado por MAX_RESULTADOS_BUSQUEDA = " + MAX_RESULTADOS_BUSQUEDA + ")");
        System.out.println();

        int resultadoNum = 1;
        for (ScoreDoc hit : hits.scoreDocs) {
            Document doc = searcher.storedFields().document(hit.doc);

            // Mostrar campos relevantes
            String name = doc.get("name");
            String description = doc.get("description");
            String neighborhoodOverview = doc.get("neighborhood_overview");
            String propertyType = doc.get("property_type");
            String neighbourhood = doc.get("neighbourhood_cleansed");
            String listingUrl = doc.get("listing_url");
            String hostId = doc.get("host_id");
            String bathroomsText = doc.get("bathrooms_text");

            // Campos multi-valorados
            String[] amenities = doc.getValues("amenity");

            // Campos numéricos almacenados como StoredField
            Double price = null;
            Double rating = null;
            Integer numberOfReviews = null;
            Integer bathrooms = null;
            Integer bedrooms = null;
            Double latitude = null;
            Double longitude = null;
            try {
                if (doc.getField("price") != null) {
                    price = doc.getField("price").numericValue().doubleValue();
                }
                if (doc.getField("review_scores_rating") != null) {
                    rating = doc.getField("review_scores_rating").numericValue().doubleValue();
                }
                if (doc.getField("number_of_reviews") != null) {
                    numberOfReviews = doc.getField("number_of_reviews").numericValue().intValue();
                }
                if (doc.getField("bathrooms") != null) {
                    bathrooms = doc.getField("bathrooms").numericValue().intValue();
                }
                if (doc.getField("bedrooms") != null) {
                    bedrooms = doc.getField("bedrooms").numericValue().intValue();
                }
                if (doc.getField("latitude") != null) {
                    latitude = doc.getField("latitude").numericValue().doubleValue();
                }
                if (doc.getField("longitude") != null) {
                    longitude = doc.getField("longitude").numericValue().doubleValue();
                }
            } catch (Exception e) {
                // Ignorar si el campo no está disponible
            }

            System.out.println("------------------------------------");
            System.out.println("RESULTADO " + resultadoNum + " de " + resultadosMostrados +
                    " (de " + totalCoincidencias + " coincidencias totales)");
            System.out.println("Doc ID (Lucene): " + hit.doc);
            if (listingUrl != null) {
                System.out.println("URL (listing_url): " + listingUrl);
            }
            if (name != null) {
                System.out.println("Nombre (name): " + name);
            }
            if (propertyType != null) {
                System.out.println("Tipo (property_type): " + propertyType);
            }
            if (neighbourhood != null) {
                System.out.println("Barrio (neighbourhood_cleansed): " + neighbourhood);
            }
            if (hostId != null) {
                System.out.println("Host ID (host_id): " + hostId);
            }
            if (price != null) {
                System.out.println("Precio (price): $" + String.format("%.2f", price));
            }
            if (bedrooms != null) {
                System.out.println("Habitaciones (bedrooms): " + bedrooms);
            }
            if (bathrooms != null) {
                System.out.println("Baños (bathrooms): " + bathrooms);
            }
            if (bathroomsText != null) {
                System.out.println("Baños texto (bathrooms_text): " + bathroomsText);
            }
            if (numberOfReviews != null) {
                System.out.println("Número de reseñas (number_of_reviews): " + numberOfReviews);
            }
            if (rating != null) {
                System.out.println("Rating (review_scores_rating): " + String.format("%.1f", rating));
            }
            if (latitude != null && longitude != null) {
                System.out.println("Ubicación (latitude, longitude): " +
                        String.format("%.6f, %.6f", latitude, longitude));
            }
            if (amenities != null && amenities.length > 0) {
                System.out.println("Amenidades (amenity) [" + amenities.length + "]: " +
                        String.join(", ", amenities));
            }
            if (description != null && description.length() > 0) {
                // Mostrar solo los primeros 200 caracteres de la descripción
                String descShort = description.length() > 200 ? description.substring(0, 200) + "..." : description;
                System.out.println("Descripción (description): " + descShort);
            }
            if (neighborhoodOverview != null && neighborhoodOverview.length() > 0) {
                // Mostrar solo los primeros 200 caracteres del overview
                String overviewShort = neighborhoodOverview.length() > 200
                        ? neighborhoodOverview.substring(0, 200) + "..."
                        : neighborhoodOverview;
                System.out.println("Resumen del barrio (neighborhood_overview): " + overviewShort);
            }
            System.out.println("Score: " + hit.score);
            System.out.println();
            resultadoNum++;
        }
    }

    /**
     * Muestra los resultados de una búsqueda en el índice de hosts
     */
    private void mostrarResultadosHosts(IndexSearcher searcher, TopDocs hits) throws IOException {
        // Obtener el total de resultados usando reflexión para acceder al campo value
        long totalCoincidencias;
        try {
            java.lang.reflect.Field valueField = hits.totalHits.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            totalCoincidencias = valueField.getLong(hits.totalHits);
        } catch (Exception e) {
            // Fallback: usar el número de resultados mostrados
            totalCoincidencias = hits.scoreDocs.length;
        }
        int resultadosMostrados = hits.scoreDocs.length;
        System.out.println("\n=== RESULTADOS ===");
        System.out.println("Total de coincidencias: " + totalCoincidencias);
        System.out.println("Mostrando: " + resultadosMostrados + " de " + totalCoincidencias +
                " (limitado por MAX_RESULTADOS_BUSQUEDA = " + MAX_RESULTADOS_BUSQUEDA + ")");
        System.out.println();

        int resultadoNum = 1;
        for (ScoreDoc hit : hits.scoreDocs) {
            Document doc = searcher.storedFields().document(hit.doc);

            // Mostrar campos relevantes de hosts
            String hostName = doc.get("host_name");
            String hostLocation = doc.get("host_location");
            String hostNeighbourhood = doc.get("host_neighbourhood");
            String hostAbout = doc.get("host_about");
            String hostResponseTime = doc.get("host_response_time");
            String hostId = doc.get("host_id");
            String hostUrl = doc.get("host_url");
            String hostSinceOriginal = doc.get("host_since_original");

            // Campos numéricos
            Integer superhost = null;
            Long hostSince = null;
            try {
                if (doc.getField("host_is_superhost") != null) {
                    superhost = doc.getField("host_is_superhost").numericValue().intValue();
                }
                if (doc.getField("host_since") != null) {
                    hostSince = doc.getField("host_since").numericValue().longValue();
                }
            } catch (Exception e) {
                // Ignorar si el campo no está disponible
            }

            System.out.println("------------------------------------");
            System.out.println("RESULTADO " + resultadoNum + " de " + resultadosMostrados +
                    " (de " + totalCoincidencias + " coincidencias totales)");
            System.out.println("Doc ID (Lucene): " + hit.doc);
            if (hostId != null) {
                System.out.println("Host ID (host_id): " + hostId);
            }
            if (hostUrl != null) {
                System.out.println("URL (host_url): " + hostUrl);
            }
            if (hostName != null) {
                System.out.println("Nombre (host_name): " + hostName);
            }
            if (hostSinceOriginal != null) {
                System.out.println("Host desde (host_since_original): " + hostSinceOriginal);
            } else if (hostSince != null) {
                // Si no hay original, mostrar el timestamp formateado como fecha
                System.out.println("Host desde (host_since): " + formatTimestamp(hostSince));
            }
            if (hostLocation != null) {
                System.out.println("Ubicación (host_location): " + hostLocation);
            }
            if (hostNeighbourhood != null) {
                System.out.println("Barrio (host_neighbourhood): " + hostNeighbourhood);
            }
            if (hostResponseTime != null) {
                System.out.println("Tiempo de respuesta (host_response_time): " + hostResponseTime);
            }
            if (superhost != null) {
                System.out.println("Superhost (host_is_superhost): " + (superhost == 1 ? "Sí" : "No"));
            }
            if (hostAbout != null && hostAbout.length() > 0) {
                // String aboutShort = hostAbout.length() > 200 ? hostAbout.substring(0, 200) +
                // "..." : hostAbout;
                // System.out.println("Acerca de (host_about): " + aboutShort);
                System.out.println("Acerca de (host_about): " + hostAbout);
            }
            System.out.println("Score: " + hit.score);
            System.out.println();
            resultadoNum++;
        }
    }

    /**
     * Menú para búsqueda general (Mega Campo)
     */
    private void menuBusquedaGeneral(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException {
        while (true) {
            System.out.println("\n=== 7. BÚSQUEDA GENERAL (MEGA CAMPO) ===");
            System.out.println("7.1. Buscar en mega campo 'contents'");
            System.out.println("7.2. Buscar en mega campo con facetas");
            System.out.println("7.3. Buscar en hosts con facetas (host_since, host_response_time)");
            System.out.println("0. Volver al menú principal");
            System.out.print("Selecciona opción: ");

            String subOption = in.readLine();
            if (subOption == null || subOption.trim().isEmpty() || "0".equals(subOption.trim())) {
                break;
            }

            try {
                switch (subOption.trim()) {
                    case "7.1":
                    case "1":
                        ejecutarQueryMegaCampo(analyzer, similarity, in);
                        break;
                    case "7.2":
                    case "2":
                        ejecutarQueryMegaCampoConFacetas(analyzer, similarity, in);
                        break;
                    case "7.3":
                    case "3":
                        ejecutarQueryMegaCampoConFacetasHosts(analyzer, similarity, in);
                        break;
                    default:
                        System.out.println("Opción no válida.");
                        continue;
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 7.1: Buscar en mega campo 'contents'
     */
    private void ejecutarQueryMegaCampo(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 7.1: Búsqueda en 'contents' (Mega Campo) ===");
        System.out.println("Este campo contiene: name, description, amenities, bathrooms, bedrooms, price, etc.");
        System.out.print("Ingrese su consulta (ej: 'pool AND 3 bathrooms'): ");
        String valor = in.readLine();

        if (valor == null || valor.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        // Lógica de búsqueda
        QueryParser parser = new QueryParser(AirbnbIndexador.FIELD_CONTENTS, analyzer);
        Query query = parser.parse(valor.trim());
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
        mostrarResultados(searcher, hits);
        reader.close();

        System.out.println("Búsqueda implementada para: " + valor.trim());
        System.out.println("Índice: Properties");
        System.out.println("Campo: " + AirbnbIndexador.FIELD_CONTENTS);
    }

    /**
     * 7.2: Buscar en mega campo con facetas
     */
    private void ejecutarQueryMegaCampoConFacetas(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 7.2: Búsqueda con Facetas (Drill-down) ===");
        System.out.print("Ingrese su consulta (ej: 'pool'): ");
        String valor = in.readLine();

        if (valor == null || valor.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);

        // Abrir taxonomía
        TaxonomyReader taxoReader = new DirectoryTaxonomyReader(
                FSDirectory.open(AirbnbIndexador.getTaxoPropertiesIndexPath(indexRoot)));
        FacetsConfig fconfig = AirbnbIndexador.createFacetsConfig();

        QueryParser parser = new QueryParser(AirbnbIndexador.FIELD_CONTENTS, analyzer);
        Query query = parser.parse(valor.trim());

        // 1. Búsqueda inicial y recolección de facetas
        FacetsCollector fc = new FacetsCollector();
        searcher.search(query, fc);

        Facets facets = new FastTaxonomyFacetCounts(taxoReader, fconfig, fc);
        List<FacetResult> allDims = facets.getAllDims(100);

        System.out.println("\nCategorias totales: " + allDims.size());
        for (FacetResult fr : allDims) {
            System.out.println("Categoria " + fr.dim);
            for (LabelAndValue lv : fr.labelValues) {
                System.out.println("    Etiq: " + lv.label + ", valor (#n)->" + lv.value);
            }
        }

        // 2. Drill-down
        System.out.println("\n¿Desea filtrar por una faceta? (s/n)");
        String resp = in.readLine();
        if (resp != null && resp.trim().equalsIgnoreCase("s")) {
            System.out.print("Ingrese la Categoría (dimensión): ");
            String dim = in.readLine();
            System.out.print("Ingrese la Etiqueta (valor): ");
            String path = in.readLine();

            if (dim != null && !dim.isEmpty() && path != null && !path.isEmpty()) {
                DrillDownQuery ddq = new DrillDownQuery(fconfig, query);
                ddq.add(dim.trim(), path.trim());

                System.out.println("Filtrando query [" + ddq.toString() + "]");

                TopDocs hits = searcher.search(ddq, MAX_RESULTADOS_BUSQUEDA);
                mostrarResultados(searcher, hits);
            }
        } else {
            // Mostrar resultados sin filtrar
            TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
            mostrarResultados(searcher, hits);
        }

        taxoReader.close();
        reader.close();
    }

    /**
     * 7.3: Buscar en hosts con facetas (host_since, host_response_time)
     */
    private void ejecutarQueryMegaCampoConFacetasHosts(Analyzer analyzer, Similarity similarity, BufferedReader in)
            throws IOException, ParseException {
        System.out.println("\n=== 7.3: Búsqueda en Hosts con Facetas ===");
        System.out.print("Ingrese su consulta (ej: 'superhost AND yoga'): ");
        String valor = in.readLine();

        if (valor == null || valor.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathHosts)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);

        // Abrir taxonomía de hosts
        TaxonomyReader taxoReader = new DirectoryTaxonomyReader(
                FSDirectory.open(AirbnbIndexador.getTaxoHostsIndexPath(indexRoot)));
        FacetsConfig fconfig = AirbnbIndexador.createFacetsConfig();

        QueryParser parser = new QueryParser(AirbnbIndexador.FIELD_CONTENTS, analyzer);
        Query query = parser.parse(valor.trim());

        // 1. Búsqueda inicial y recolección de facetas
        FacetsCollector fc = new FacetsCollector();
        searcher.search(query, fc);

        // Faceta 1: host_response_time (Taxonomy)
        Facets facetsResponseTime = new FastTaxonomyFacetCounts(taxoReader, fconfig, fc);
        FacetResult frResponseTime = facetsResponseTime.getTopChildren(10, "host_response_time");

        // Faceta 2: host_since (Ranges)
        Facets facetsHostSince = new LongRangeFacetCounts("host_since", fc, AirbnbIndexador.getHostSinceRanges());
        FacetResult frHostSince = facetsHostSince.getTopChildren(10, "host_since");

        System.out.println("\n--- Facetas Disponibles ---");

        if (frResponseTime != null) {
            System.out.println("Categoría: host_response_time");
            for (LabelAndValue lv : frResponseTime.labelValues) {
                System.out.println("    " + lv.label + " (" + lv.value + ")");
            }
        }

        if (frHostSince != null) {
            System.out.println("Categoría: host_since");
            for (LabelAndValue lv : frHostSince.labelValues) {
                System.out.println("    " + lv.label + " (" + lv.value + ")");
            }
        }

        // 2. Drill-down
        System.out.println("\n¿Desea filtrar por una faceta? (s/n)");
        String resp = in.readLine();
        if (resp != null && resp.trim().equalsIgnoreCase("s")) {
            System.out.println("Opciones: 1. host_response_time, 2. host_since");
            System.out.print("Seleccione faceta (1/2): ");
            String facetaOpt = in.readLine();

            if ("1".equals(facetaOpt)) {
                System.out.print("Ingrese el valor exacto (ej: 'within an hour'): ");
                String path = in.readLine();
                if (path != null && !path.isEmpty()) {
                    DrillDownQuery ddq = new DrillDownQuery(fconfig, query);
                    ddq.add("host_response_time", path.trim());
                    System.out.println("Filtrando por host_response_time: " + path);
                    TopDocs hits = searcher.search(ddq, MAX_RESULTADOS_BUSQUEDA);
                    mostrarResultadosHosts(searcher, hits);
                }
            } else if ("2".equals(facetaOpt)) {
                System.out.print("Ingrese el rango (ej: '2015-2020'): ");
                String rangeLabel = in.readLine();
                if (rangeLabel != null && !rangeLabel.isEmpty()) {
                    // Encontrar el rango correspondiente
                    LongRange[] ranges = AirbnbIndexador.getHostSinceRanges();
                    LongRange selectedRange = null;
                    for (LongRange r : ranges) {
                        if (r.label.equals(rangeLabel.trim())) {
                            selectedRange = r;
                            break;
                        }
                    }

                    if (selectedRange != null) {
                        // Crear DrillDownQuery manualmente para rangos
                        // DrillDownQuery soporta rangos si se configuran, pero a veces es más fácil
                        // combinar la query original con un filtro de rango

                        // Opción A: Usar DrillDownQuery si FacetsConfig lo soportara directamente (más
                        // complejo para rangos dinámicos)
                        // Opción B: Combinar BooleanQuery (Query original + RangeQuery)

                        BooleanQuery.Builder builder = new BooleanQuery.Builder();
                        builder.add(query, BooleanClause.Occur.MUST);
                        builder.add(LongPoint.newRangeQuery("host_since", selectedRange.min, selectedRange.max),
                                BooleanClause.Occur.FILTER);

                        System.out.println("Filtrando por host_since: " + rangeLabel);
                        TopDocs hits = searcher.search(builder.build(), MAX_RESULTADOS_BUSQUEDA);
                        mostrarResultadosHosts(searcher, hits);
                    } else {
                        System.out.println("Rango no válido.");
                    }
                }
            } else {
                System.out.println("Opción no válida.");
            }
        } else {
            // Mostrar resultados sin filtrar
            TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
            mostrarResultadosHosts(searcher, hits);
        }

        taxoReader.close();
        reader.close();
    }
}
