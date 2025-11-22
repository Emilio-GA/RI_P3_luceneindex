import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LatLonPoint;
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

/**
 * Clase básica de búsqueda Lucene para el índice de Airbnb
 * 
 * Busca en el índice de propiedades (index_properties) creado por AirbnbIndexador.
 * 
 * COMPILACIÓN:
 *   NOTA: Maven tiene problemas compilando esta clase directamente. Use este workaround:
 *   
 *   1. Compile el resto del proyecto con Maven:
 *      mvn clean compile
 *   
 *   2. CRÍTICO: Elimine el archivo compilado anterior (Maven genera uno incorrecto):
 *      rm -f target/classes/BusquedasLucene.class
 *   
 *   3. Compile BusquedasLucene manualmente con Java 21:
 *      mvn dependency:build-classpath -DincludeScope=compile -q -Dmdep.outputFile=/tmp/cp.txt
 *      javac -cp "target/classes:$(cat /tmp/cp.txt)" -d target/classes --release 21 src/main/java/BusquedasLucene.java
 * 
 * COMANDO COMPLETO (todos los pasos en uno):
 *   mvn clean compile && rm -f target/classes/BusquedasLucene.class && mvn dependency:build-classpath -DincludeScope=compile -q -Dmdep.outputFile=/tmp/cp.txt && javac -cp "target/classes:$(cat /tmp/cp.txt)" -d target/classes --release 21 src/main/java/BusquedasLucene.java
 * 
 * EJECUCIÓN:
 *   NOTA: mvn exec:java no funciona debido al mismo problema de compilación.
 *   IMPORTANTE: NO ejecute "mvn dependency:build-classpath" de nuevo, ya que recompila
 *   y sobrescribe el archivo correcto. Use el classpath generado en el paso 3.
 *   
 *   Ejecutar directamente con java (el classpath ya está en /tmp/cp.txt del paso 3):
 *      java -cp "target/classes:$(cat /tmp/cp.txt)" BusquedasLucene --index-root ./index_root
 * 
 * COMANDO COMPLETO (compilar y ejecutar en uno):
 *   mvn clean compile && rm -f target/classes/BusquedasLucene.class && mvn dependency:build-classpath -DincludeScope=compile -q -Dmdep.outputFile=/tmp/cp.txt && javac -cp "target/classes:$(cat /tmp/cp.txt)" -d target/classes --release 21 src/main/java/BusquedasLucene.java && java -cp "target/classes:$(cat /tmp/cp.txt)" BusquedasLucene --index-root ./index_root
 *   
 *   Si necesita regenerar el classpath (sin recompilar), puede usar:
 *      mvn dependency:build-classpath -DincludeScope=compile -q -Dmdep.outputFile=/tmp/cp.txt -DskipTests
 *      Pero luego DEBE recompilar manualmente BusquedasLucene de nuevo (paso 3)
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
        
        // Reutilizar el analizador y similarity del indexador para garantizar consistencia
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
                            menuBooleanQueries(analyzer, in);
                            break;
                        case "4":
                            menuQueriesOrdenadas(analyzer, similarity, in);
                            break;
                        case "5":
                            menuQueriesGeograficas(in);
                            break;
                        case "6":
                            menuQueriesMultiIndice(analyzer, similarity, in);
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

        // TODO: Implementar la lógica de búsqueda
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

        // TODO: Implementar la lógica de búsqueda
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

        // TODO: Implementar la lógica de búsqueda
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
            System.out.println("2.4. Búsqueda por rango en campo 'host_since' (Hosts) - formato MIN-MAX");
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
     * 2.1: Búsqueda en campo 'price' en índice de Properties con operadores de comparación
     * Formatos aceptados:
     *   - 120 o =120: precio igual a 120
     *   - >120: precio mayor que 120
     *   - >=120: precio mayor o igual a 120
     *   - <120: precio menor que 120
     *   - <=120: precio menor o igual a 120
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
            Query query;
            String operador = "";
            double precio;
            
            // Detectar operador y extraer el valor
            if (input.startsWith(">=")) {
                operador = ">=";
                precio = Double.parseDouble(input.substring(2).trim());
                // Precio mayor o igual: desde precio hasta Double.MAX_VALUE
                query = DoublePoint.newRangeQuery("price", precio, Double.MAX_VALUE);
            } else if (input.startsWith("<=")) {
                operador = "<=";
                precio = Double.parseDouble(input.substring(2).trim());
                // Precio menor o igual: desde 0 hasta precio
                query = DoublePoint.newRangeQuery("price", 0.0, precio);
            } else if (input.startsWith(">")) {
                operador = ">";
                precio = Double.parseDouble(input.substring(1).trim());
                // Precio mayor: desde precio+epsilon hasta Double.MAX_VALUE
                // Usamos un pequeño incremento para excluir el valor exacto
                double precioMin = precio + 0.01;
                query = DoublePoint.newRangeQuery("price", precioMin, Double.MAX_VALUE);
            } else if (input.startsWith("<")) {
                operador = "<";
                precio = Double.parseDouble(input.substring(1).trim());
                // Precio menor: desde 0 hasta precio-epsilon
                // Usamos un pequeño decremento para excluir el valor exacto
                double precioMax = precio - 0.01;
                query = DoublePoint.newRangeQuery("price", 0.0, precioMax);
            } else if (input.startsWith("=")) {
                operador = "=";
                precio = Double.parseDouble(input.substring(1).trim());
                // Precio exacto: rango de precio a precio
                query = DoublePoint.newRangeQuery("price", precio, precio);
            } else {
                // Sin operador explícito, asumir igualdad
                operador = "=";
                precio = Double.parseDouble(input);
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
            
            // TODO: Implementar la lógica de búsqueda
            // Query query = DoublePoint.newRangeQuery("price", min, max);
            // IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
            // IndexSearcher searcher = new IndexSearcher(reader);
            // searcher.setSimilarity(similarity);
            // TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
            // mostrarResultados(searcher, hits);
            // reader.close();
            
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
        System.out.print("Ingrese el valor (0 = no superhost, 1 = superhost): ");
        String valorStr = in.readLine();
        
        if (valorStr == null || valorStr.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        try {
            int valor = Integer.parseInt(valorStr.trim());
            if (valor != 0 && valor != 1) {
                System.out.println("Error: el valor debe ser 0 o 1.");
                return;
            }
            
            // TODO: Implementar la lógica de búsqueda
            // Query query = IntPoint.newRangeQuery("host_is_superhost", valor, valor);
            // IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathHosts)));
            // IndexSearcher searcher = new IndexSearcher(reader);
            // searcher.setSimilarity(similarity);
            // TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
            // mostrarResultadosHosts(searcher, hits);
            // reader.close();
            
            System.out.println("Búsqueda implementada para host_is_superhost: " + valor + 
                (valor == 1 ? " (superhost)" : " (no superhost)"));
            System.out.println("Índice: Hosts");
            System.out.println("Campo: host_is_superhost");
        } catch (NumberFormatException e) {
            System.out.println("Error: debe ingresar un número entero válido (0 o 1).");
        }
    }

    /**
     * 2.4: Búsqueda por rango en campo 'host_since' en índice de Hosts
     * Formato de entrada: "MIN-MAX" donde MIN y MAX son timestamps en epoch millis
     * Ejemplo: "1199145600000-1609459200000" (2008-01-01 a 2020-12-31)
     */
    private void ejecutarQueryHostSinceRango(Analyzer analyzer, Similarity similarity, BufferedReader in) 
            throws IOException {
        System.out.println("\n=== 2.4: Búsqueda por rango en 'host_since' (Hosts) ===");
        System.out.println("Nota: host_since está almacenado como epoch millis (LongPoint)");
        System.out.println("      Ingrese timestamps en formato MIN-MAX (ej: 1199145600000-1609459200000)");
        System.out.print("Ingrese el rango en formato MIN-MAX: ");
        String rangoStr = in.readLine();
        
        if (rangoStr == null || rangoStr.trim().isEmpty()) {
            System.out.println("Valor vacío. Cancelando búsqueda.");
            return;
        }

        try {
            String[] partes = rangoStr.trim().split("-");
            if (partes.length != 2) {
                System.out.println("Error: formato incorrecto. Use MIN-MAX (ej: 1199145600000-1609459200000)");
                return;
            }
            
            long min = Long.parseLong(partes[0].trim());
            long max = Long.parseLong(partes[1].trim());
            
            if (min > max) {
                System.out.println("Error: el valor mínimo no puede ser mayor que el máximo.");
                return;
            }
            
            // TODO: Implementar la lógica de búsqueda
            // Query query = LongPoint.newRangeQuery("host_since", min, max);
            // IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathHosts)));
            // IndexSearcher searcher = new IndexSearcher(reader);
            // searcher.setSimilarity(similarity);
            // TopDocs hits = searcher.search(query, MAX_RESULTADOS_BUSQUEDA);
            // mostrarResultadosHosts(searcher, hits);
            // reader.close();
            
            System.out.println("Búsqueda implementada para rango de host_since: " + min + " - " + max);
            System.out.println("Índice: Hosts");
            System.out.println("Campo: host_since");
        } catch (NumberFormatException e) {
            System.out.println("Error: los valores MIN y MAX deben ser números válidos (timestamps en epoch millis).");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Menú para BooleanQueries
     */
    private void menuBooleanQueries(Analyzer analyzer, BufferedReader in) throws IOException {
        System.out.println("\n=== 3. BOOLEANQUERIES ===");
        System.out.println("Funcionalidad pendiente de implementar");
        // TODO: Implementar submenú con opciones para BooleanQueries con distintos campos y BooleanClause
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
            throws IOException {
        System.out.println("\n=== 4.1: Ordenar por 'number_of_reviews' DESC (Properties) ===");
        System.out.println("Nota: Si ingresa una consulta, se buscará en el campo 'description' por defecto.");
        System.out.println("      Si deja la consulta vacía (Enter), se devolverán todos los documentos.");
        System.out.print("Ingrese la consulta textual (o Enter para buscar todos): ");
        String consulta = in.readLine();
        
        Query query = null;
        if (consulta != null && !consulta.trim().isEmpty()) {
            // TODO: Parsear la consulta si se proporciona
            // QueryParser parser = new QueryParser("description", analyzer);
            // query = parser.parse(consulta.trim());
        } else {
            // TODO: Crear query que devuelva todos los documentos
            // query = new MatchAllDocsQuery();
        }
        
        // TODO: Implementar la lógica de búsqueda con Sort
        // Sort sort = new Sort(new SortField("number_of_reviews", SortField.Type.INT, true)); // true = descendente
        // IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathProperties)));
        // IndexSearcher searcher = new IndexSearcher(reader);
        // searcher.setSimilarity(similarity);
        // TopDocs hits = searcher.search(query, 100, sort);
        // mostrarResultados(searcher, hits);
        // reader.close();
        
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
            throws IOException {
        System.out.println("\n=== 4.2: Ordenar por 'host_since' ASC (Hosts) ===");
        System.out.println("Nota: Si ingresa una consulta, se buscará en el campo 'host_name' por defecto.");
        System.out.println("      Si deja la consulta vacía (Enter), se devolverán todos los documentos.");
        System.out.print("Ingrese la consulta textual (o Enter para buscar todos): ");
        String consulta = in.readLine();
        
        Query query = null;
        if (consulta != null && !consulta.trim().isEmpty()) {
            // TODO: Parsear la consulta si se proporciona
            // QueryParser parser = new QueryParser("host_name", analyzer);
            // query = parser.parse(consulta.trim());
        } else {
            // TODO: Crear query que devuelva todos los documentos
            // query = new MatchAllDocsQuery();
        }
        
        // TODO: Implementar la lógica de búsqueda con Sort
        // Sort sort = new Sort(new SortField("host_since", SortField.Type.LONG, false)); // false = ascendente (más antiguo primero)
        // IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPathHosts)));
        // IndexSearcher searcher = new IndexSearcher(reader);
        // searcher.setSimilarity(similarity);
        // TopDocs hits = searcher.search(query, 100, sort);
        // mostrarResultadosHosts(searcher, hits);
        // reader.close();
        
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
    private void menuQueriesGeograficas(BufferedReader in) throws IOException {
        System.out.println("\n=== 5. CONSULTAS GEOGRÁFICAS ===");
        System.out.println("Funcionalidad pendiente de implementar");
        // TODO: Implementar submenú con opciones para queries geográficas
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
            String[] campos = {"name", "description", "neighborhood_overview", "host_name", "host_about"};
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
                if (readerProperties != null) readerProperties.close();
                if (readerHosts != null) readerHosts.close();
            }
        }
    }

    /**
     * Muestra los resultados de una búsqueda multi-índice
     * Detecta automáticamente si el documento viene del índice de Properties o Hosts
     * basándose en el doc ID (si es menor que numDocsProperties, viene de Properties)
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
                    String descShort = description.length() > 200 ? 
                        description.substring(0, 200) + "..." : description;
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
                    String aboutShort = hostAbout.length() > 200 ? 
                        hostAbout.substring(0, 200) + "..." : hostAbout;
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
    private Query crearQueryTextualMultiCampo(MultiFieldQueryParser parser, BufferedReader in) 
            throws IOException, org.apache.lucene.queryparser.classic.ParseException {
        System.out.print("Consulta multi-campo (ej: 'beach'): ");
        String line = in.readLine();
        if (line == null || line.trim().isEmpty()) return null;
        return parser.parse(line.trim());
    }

    /**
     * Query numérica exacta (búsqueda de un valor específico)
     * Ejemplo: precio exacto = 150.0
     */
    private Query crearQueryNumericaExacta(BufferedReader in) throws IOException {
        System.out.println("Búsqueda numérica exacta:");
        System.out.print("Campo (price, review_scores_rating, bedrooms, bathrooms, number_of_reviews): ");
        String campo = in.readLine();
        if (campo == null || campo.trim().isEmpty()) return null;
        
        System.out.print("Valor exacto: ");
        String valorStr = in.readLine();
        if (valorStr == null || valorStr.trim().isEmpty()) return null;
        
        try {
            double valor = Double.parseDouble(valorStr.trim());
            
            // Determinar si es IntPoint o DoublePoint basado en el campo
            if (campo.equals("price") || campo.equals("review_scores_rating")) {
                // DoublePoint: usar rango muy pequeño para búsqueda "exacta"
                return DoublePoint.newRangeQuery(campo, valor, valor);
            } else {
                // IntPoint: usar rango muy pequeño para búsqueda "exacta"
                int valorInt = (int) valor;
                return IntPoint.newRangeQuery(campo, valorInt, valorInt);
            }
        } catch (NumberFormatException e) {
            System.out.println("Error: valor numérico inválido");
            return null;
        }
    }

    /**
     * Query numérica por rango
     * Ejemplo: precio entre 100 y 200
     */
    private Query crearQueryNumericaRango(BufferedReader in) throws IOException {
        System.out.println("Búsqueda numérica por rango:");
        System.out.print("Campo (price, review_scores_rating, bedrooms, bathrooms, number_of_reviews): ");
        String campo = in.readLine();
        if (campo == null || campo.trim().isEmpty()) return null;
        
        System.out.print("Valor mínimo (o * para sin límite): ");
        String minStr = in.readLine();
        System.out.print("Valor máximo (o * para sin límite): ");
        String maxStr = in.readLine();
        
        try {
            if (campo.equals("price") || campo.equals("review_scores_rating")) {
                // DoublePoint
                double min = minStr == null || minStr.trim().equals("*") ? 
                    Double.NEGATIVE_INFINITY : Double.parseDouble(minStr.trim());
                double max = maxStr == null || maxStr.trim().equals("*") ? 
                    Double.POSITIVE_INFINITY : Double.parseDouble(maxStr.trim());
                return DoublePoint.newRangeQuery(campo, min, max);
            } else {
                // IntPoint
                int min = minStr == null || minStr.trim().equals("*") ? 
                    Integer.MIN_VALUE : Integer.parseInt(minStr.trim());
                int max = maxStr == null || maxStr.trim().equals("*") ? 
                    Integer.MAX_VALUE : Integer.parseInt(maxStr.trim());
                return IntPoint.newRangeQuery(campo, min, max);
            }
        } catch (NumberFormatException e) {
            System.out.println("Error: valor numérico inválido");
            return null;
        }
    }

    /**
     * BooleanQuery con configuración de MUST/SHOULD
     * Ejemplo: (name:beach MUST) AND (description:pool SHOULD)
     */
    private Query crearBooleanQuery(Analyzer analyzer, BufferedReader in) 
            throws IOException, org.apache.lucene.queryparser.classic.ParseException {
        System.out.println("BooleanQuery - Configuración de cláusulas:");
        System.out.println("Ejemplo: 'name:beach MUST' y 'description:pool SHOULD'");
        System.out.print("Número de cláusulas: ");
        String numStr = in.readLine();
        int numClausulas = Integer.parseInt(numStr.trim());
        
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        QueryParser parser = new QueryParser("description", analyzer);
        
        for (int i = 0; i < numClausulas; i++) {
            System.out.print("Cláusula " + (i+1) + " (query): ");
            String queryStr = in.readLine();
            System.out.print("Tipo (MUST/SHOULD/MUST_NOT): ");
            String tipo = in.readLine();
            
            Query query = parser.parse(queryStr.trim());
            BooleanClause.Occur occur;
            switch (tipo.trim().toUpperCase()) {
                case "MUST":
                    occur = BooleanClause.Occur.MUST;
                    break;
                case "SHOULD":
                    occur = BooleanClause.Occur.SHOULD;
                    break;
                case "MUST_NOT":
                    occur = BooleanClause.Occur.MUST_NOT;
                    break;
                default:
                    occur = BooleanClause.Occur.SHOULD;
            }
            builder.add(query, occur);
        }
        
        return builder.build();
    }

    /**
     * Query geográfica usando LatLonPoint
     * Ejemplo: propiedades dentro de 5km de (34.0522, -118.2437) - Los Angeles
     */
    private Query crearQueryGeografica(BufferedReader in) throws IOException {
        System.out.println("Búsqueda geográfica:");
        System.out.print("Latitud: ");
        String latStr = in.readLine();
        System.out.print("Longitud: ");
        String lonStr = in.readLine();
        System.out.print("Radio en metros (ej: 5000 para 5km): ");
        String radioStr = in.readLine();
        
        try {
            double lat = Double.parseDouble(latStr.trim());
            double lon = Double.parseDouble(lonStr.trim());
            double radioMetros = Double.parseDouble(radioStr.trim());
            
            return LatLonPoint.newDistanceQuery("location", lat, lon, radioMetros);
        } catch (NumberFormatException e) {
            System.out.println("Error: valor numérico inválido");
            return null;
        }
    }

    /**
     * Crea un Sort para ordenar por un campo distinto al score
     * Ejemplo: ordenar por review_scores_rating descendente
     */
    private Sort crearSort(BufferedReader in) throws IOException {
        System.out.println("Ordenamiento:");
        System.out.print("Campo (review_scores_rating, price, number_of_reviews, bedrooms): ");
        String campo = in.readLine();
        System.out.print("Orden (ASC/DESC): ");
        String orden = in.readLine();
        
        boolean reverse = orden != null && orden.trim().toUpperCase().equals("DESC");
        
        // Determinar tipo de campo
        SortField.Type type;
        if (campo.equals("price") || campo.equals("review_scores_rating")) {
            type = SortField.Type.DOUBLE;
        } else {
            type = SortField.Type.INT;
        }
        
        return new Sort(new SortField(campo, type, reverse));
    }

    /**
     * Query combinada: texto + numérica + geográfica
     * Ejemplo: "apartment" AND price:[100 TO 200] AND location within 5km
     */
    private Query crearQueryCombinada(Analyzer analyzer, BufferedReader in) 
            throws IOException, org.apache.lucene.queryparser.classic.ParseException {
        System.out.println("Query combinada:");
        
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        QueryParser parser = new QueryParser("description", analyzer);
        
        // Query textual
        System.out.print("Consulta textual (o Enter para omitir): ");
        String texto = in.readLine();
        if (texto != null && !texto.trim().isEmpty()) {
            builder.add(parser.parse(texto.trim()), BooleanClause.Occur.MUST);
        }
        
        // Query numérica por rango
        System.out.print("¿Incluir filtro numérico? (s/n): ");
        String incluirNum = in.readLine();
        if (incluirNum != null && incluirNum.trim().toLowerCase().equals("s")) {
            Query numQuery = crearQueryNumericaRango(in);
            if (numQuery != null) {
                builder.add(numQuery, BooleanClause.Occur.MUST);
            }
        }
        
        // Query geográfica
        System.out.print("¿Incluir filtro geográfico? (s/n): ");
        String incluirGeo = in.readLine();
        if (incluirGeo != null && incluirGeo.trim().toLowerCase().equals("s")) {
            Query geoQuery = crearQueryGeografica(in);
            if (geoQuery != null) {
                builder.add(geoQuery, BooleanClause.Occur.MUST);
            }
        }
        
        return builder.build();
    }

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
                String descShort = description.length() > 200 ? 
                    description.substring(0, 200) + "..." : description;
                System.out.println("Descripción (description): " + descShort);
            }
            if (neighborhoodOverview != null && neighborhoodOverview.length() > 0) {
                // Mostrar solo los primeros 200 caracteres del overview
                String overviewShort = neighborhoodOverview.length() > 200 ? 
                    neighborhoodOverview.substring(0, 200) + "..." : neighborhoodOverview;
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
                // Si no hay original, mostrar el timestamp
                System.out.println("Host desde (host_since): " + hostSince + " (epoch millis)");
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
                String aboutShort = hostAbout.length() > 200 ? 
                    hostAbout.substring(0, 200) + "..." : hostAbout;
                System.out.println("Acerca de (host_about): " + aboutShort);
            }
            System.out.println("Score: " + hit.score);
            System.out.println();
            resultadoNum++;
        }
    }
}

