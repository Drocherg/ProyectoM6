package org.neo4j.driver.neo4j_java_driver;

import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.List;
import java.util.Scanner;

@SuppressWarnings("CallToPrintStackTrace")
public class Neo4jRedSocial {
    // Configuración para la conexión con la base de datos Neo4j en la nube
    private static final String NEO4J_URI = "neo4j+s://2f7d5d3c.databases.neo4j.io";
    private static final String USER = "neo4j";
    private static final String PASSWORD = "rfsvGgaI6Wwj76OCyDp_Vy0zvhSDioGy5V_ypGlI8bg";

    public static void main(String[] args) {
        // Conexión al driver de Neo4j y uso de Scanner para entrada por consola
        try (Driver driver = GraphDatabase.driver(NEO4J_URI, AuthTokens.basic(USER, PASSWORD));
             Session session = driver.session();
             Scanner scanner = new Scanner(System.in)) {

            System.out.print("Introduce tu ID de usuario: ");
            String currentUser = scanner.nextLine();
            insertarUsuario(session, currentUser); // Crea el usuario si no existe

            boolean salir = false;
            while (!salir) {
                // Menú de opciones principal
                System.out.println("\n=== MENU ===");
                System.out.println("1. Ver publicaciones");
                System.out.println("2. Subir foto");
                System.out.println("3. Eliminar publicación");
                System.out.println("4. Ver publicaciones de otro usuario");
                System.out.println("5. Salir");
                System.out.print("Opción: ");
                String opcion = scanner.nextLine();

                switch (opcion) {
                    case "1":
                        verPublicaciones(session, scanner, currentUser);
                        break;
                    case "2":
                        subirFoto(session, scanner, currentUser);
                        break;
                    case "3":
                        eliminarPublicacion(session, scanner, currentUser);
                        break;
                    case "4":
                        verPublicacionesDeUsuario(session, scanner);
                        break;
                    case "5":
                        salir = true;
                        break;
                    default:
                        System.out.println("Opción no valida.");
                }
            }
        } catch (Exception e) {
            System.out.println("Error al interactuar con Neo4j: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void insertarUsuario(Session session, String userId) {
        // Crea un nodo User si no existe ya
        session.writeTransaction(tx -> {
            tx.run("MERGE (u:User {id: $userId})", Values.parameters("userId", userId));
            return null;
        });
        System.out.println("Usuario registrado: " + userId);
    }

    private static void subirFoto(Session session, Scanner scanner, String userId) {
        // Crea una nueva publicación (Post) y la relaciona con el usuario
        System.out.print("ID de la publicación: ");
        String postId = scanner.nextLine();

        System.out.print("Contenido de la publicación: ");
        String contenido = scanner.nextLine();

        session.writeTransaction(tx -> {
            tx.run("MATCH (u:User {id: $userId}) " +
                            "CREATE (p:Post {id: $postId, contenido: $contenido}) " +
                            "MERGE (u)-[:PUBLICÓ]->(p)",
                    Values.parameters("userId", userId, "postId", postId, "contenido", contenido));
            return null;
        });

        System.out.println("Publicación subida.");
    }

    private static void verPublicacionesDeUsuario(Session session, Scanner scanner) {
        // Muestra publicaciones de otro usuario con opción a comentar o dar like
        System.out.print("Introduce el ID del usuario para ver sus publicaciones: ");
        String targetUserId = scanner.nextLine();

        List<Record> posts = session.readTransaction(tx ->
                tx.run("MATCH (u:User {id: $userId})-[:PUBLICÓ]->(p:Post) " +
                                "OPTIONAL MATCH (p)<-[:LIKED]-(l:User) " +
                                "RETURN p.id AS postId, p.contenido AS contenido, COUNT(l) AS likes " +
                                "ORDER BY likes DESC",
                        Values.parameters("userId", targetUserId)).list()
        );

        if (posts.isEmpty()) {
            System.out.println("Este usuario no tiene publicaciones.");
            return;
        }

        System.out.println("\nPublicaciones de " + targetUserId + ":");
        for (int i = 0; i < posts.size(); i++) {
            Record post = posts.get(i);
            System.out.printf("%d. %s (Likes: %d)\n",
                    i + 1,
                    post.get("contenido").asString(),
                    post.get("likes").asInt());
        }

        System.out.print("Selecciona una publicación para comentar o dar like (o Enter para salir): ");
        String seleccion = scanner.nextLine();

        if (!seleccion.isEmpty()) {
            try {
                int index = Integer.parseInt(seleccion) - 1;
                if (index >= 0 && index < posts.size()) {
                    String postId = posts.get(index).get("postId").asString();

                    // Mostrar comentarios existentes en la publicación
                    List<Record> comentarios = session.readTransaction(tx ->
                            tx.run("MATCH (c:Comment)-[:EN]->(p:Post {id: $postId}) " +
                                            "RETURN c.contenido AS contenido",
                                    Values.parameters("postId", postId)).list()
                    );

                    System.out.println("Comentarios:");
                    if (comentarios.isEmpty()) {
                        System.out.println("  (Sin comentarios)");
                    } else {
                        for (Record c : comentarios) {
                            System.out.println("  - " + c.get("contenido").asString());
                        }
                    }

                    System.out.print("¿Deseas comentar (c), dar like (l) o salir (Enter)? ");
                    String accion = scanner.nextLine();

                    if (accion.equalsIgnoreCase("c")) {
                        System.out.print("Introduce tu ID de usuario: ");
                        String currentUserId = scanner.nextLine();
                        System.out.print("Escribe tu comentario: ");
                        String comentario = scanner.nextLine();

                        // Crear comentario y relaciones con usuario y publicación
                        session.writeTransaction(tx -> {
                            tx.run("MATCH (u:User {id: $userId}), (p:Post {id: $postId}) " +
                                            "CREATE (c:Comment {contenido: $contenido}) " +
                                            "CREATE (u)-[:COMENTÓ]->(c) " +
                                            "CREATE (c)-[:EN]->(p)",
                                    Values.parameters("userId", currentUserId, "postId", postId, "contenido", comentario));
                            return null;
                        });

                        System.out.println("Comentario añadido.");
                    } else if (accion.equalsIgnoreCase("l")) {
                        System.out.print("Introduce tu ID de usuario: ");
                        String currentUserId = scanner.nextLine();

                        // Relación de like desde el usuario a la publicación
                        session.writeTransaction(tx -> {
                            tx.run("MATCH (u:User {id: $userId}), (p:Post {id: $postId}) " +
                                            "MERGE (u)-[:LIKED]->(p)",
                                    Values.parameters("userId", currentUserId, "postId", postId));
                            return null;
                        });

                        System.out.println("¡Has dado like a la publicación!");
                    }
                } else {
                    System.out.println("Selección inválida.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Entrada inválida.");
            }
        }
    }

    private static void verPublicaciones(Session session, Scanner scanner, String userId) {
        // Muestra todas las publicaciones de la red con número de likes
        List<Record> posts = session.readTransaction(tx ->
                tx.run("MATCH (p:Post) " +
                        "OPTIONAL MATCH (p)<-[:LIKED]-(u:User) " +
                        "RETURN p.id AS postId, p.contenido AS contenido, COUNT(u) AS likes " +
                        "ORDER BY likes DESC").list()
        );

        if (posts.isEmpty()) {
            System.out.println("No hay publicaciones.");
            return;
        }

        System.out.println("\nPublicaciones en la red:");
        for (int i = 0; i < posts.size(); i++) {
            Record post = posts.get(i);
            System.out.printf("%d. %s (Likes: %d)\n",
                    i + 1,
                    post.get("contenido").asString(),
                    post.get("likes").asInt());
        }

        System.out.print("Selecciona una publicación para comentar o dar like (o Enter para saltar): ");
        String seleccion = scanner.nextLine();

        if (!seleccion.isEmpty()) {
            try {
                int index = Integer.parseInt(seleccion) - 1;
                if (index >= 0 && index < posts.size()) {
                    String postId = posts.get(index).get("postId").asString();

                    // Comentarios existentes
                    List<Record> comentarios = session.readTransaction(tx ->
                            tx.run("MATCH (c:Comment)-[:EN]->(p:Post {id: $postId}) " +
                                                    "RETURN c.contenido AS contenido",
                                            Values.parameters("postId", postId))
                                    .list());

                    System.out.println("Comentarios:");
                    if (comentarios.isEmpty()) {
                        System.out.println("  (Sin comentarios)");
                    } else {
                        for (Record c : comentarios) {
                            System.out.println("  - " + c.get("contenido").asString());
                        }
                    }

                    System.out.print("¿Deseas comentar (c), dar like (l) o salir (Enter)? ");
                    String accion = scanner.nextLine();

                    if (accion.equalsIgnoreCase("c")) {
                        System.out.print("Escribe tu comentario: ");
                        String comentario = scanner.nextLine();

                        // Agrega comentario
                        session.writeTransaction(tx -> {
                            tx.run("MATCH (u:User {id: $userId}), (p:Post {id: $postId}) " +
                                            "CREATE (c:Comment {contenido: $contenido}) " +
                                            "CREATE (u)-[:COMENTÓ]->(c) " +
                                            "CREATE (c)-[:EN]->(p)",
                                    Values.parameters("userId", userId, "postId", postId, "contenido", comentario));
                            return null;
                        });

                        System.out.println("Comentario añadido.");
                    } else if (accion.equalsIgnoreCase("l")) {
                        // Agrega like
                        session.writeTransaction(tx -> {
                            tx.run("MATCH (u:User {id: $userId}), (p:Post {id: $postId}) " +
                                            "MERGE (u)-[:LIKED]->(p)",
                                    Values.parameters("userId", userId, "postId", postId));
                            return null;
                        });
                        System.out.println("¡Has dado like a la publicación!");
                    }
                } else {
                    System.out.println("Selección inválida.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Entrada inválida.");
            }
        }
    }

    private static void eliminarPublicacion(Session session, Scanner scanner, String userId) {
        // Muestra las publicaciones del usuario para seleccionar cuál eliminar
        List<Record> posts = session.readTransaction(tx ->
                tx.run("MATCH (u:User {id: $userId})-[:PUBLICÓ]->(p:Post) " +
                                "RETURN p.id AS postId, p.contenido AS contenido ORDER BY p.id",
                        Values.parameters("userId", userId)).list()
        );

        if (posts.isEmpty()) {
            System.out.println("No tienes publicaciones para eliminar.");
            return;
        }

        System.out.println("\nTus publicaciones:");
        for (int i = 0; i < posts.size(); i++) {
            Record post = posts.get(i);
            System.out.printf("%d. %s\n", i + 1, post.get("contenido").asString());
        }

        System.out.print("Selecciona una publicación para eliminar (o Enter para cancelar): ");
        String seleccion = scanner.nextLine();
        if (seleccion.isEmpty()) return;

        try {
            int index = Integer.parseInt(seleccion) - 1;
            if (index >= 0 && index < posts.size()) {
                String postId = posts.get(index).get("postId").asString();

                System.out.print("¿Estás seguro de que quieres eliminar esta publicación? (s/n): ");
                String confirmacion = scanner.nextLine();

                if (confirmacion.equalsIgnoreCase("s")) {
                    // Elimina comentarios, likes y la publicación
                    session.writeTransaction(tx -> {
                        tx.run("MATCH (c:Comment)-[:EN]->(p:Post {id: $postId}) DETACH DELETE c",
                                Values.parameters("postId", postId));
                        tx.run("MATCH (:User)-[l:LIKED]->(p:Post {id: $postId}) DELETE l",
                                Values.parameters("postId", postId));
                        tx.run("MATCH (p:Post {id: $postId}) DETACH DELETE p",
                                Values.parameters("postId", postId));
                        return null;
                    });

                    System.out.println("Publicación y relaciones eliminadas.");
                } else {
                    System.out.println("Eliminación cancelada.");
                }
            } else {
                System.out.println("Selección inválida.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Entrada inválida.");
        }
    }
}
