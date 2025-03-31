package org.neo4j.driver.neo4j_java_driver;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.util.List;

public class Neo4jRedSocial {
    private static final String NEO4J_URI = "neo4j+s://2f7d5d3c.databases.neo4j.io";
    private static final String USER = "neo4j";
    private static final String PASSWORD = "rfsvGgaI6Wwj76OCyDp_Vy0zvhSDioGy5V_ypGlI8bg";

    public static void main(String[] args) {
        try (Driver driver = GraphDatabase.driver(NEO4J_URI, AuthTokens.basic(USER, PASSWORD));
             Session session = driver.session()) {

            // Insertar usuarios
            insertarUsuario(session, "user123");
            insertarUsuario(session, "user456");
            insertarUsuario(session, "user789");

            // Insertar publicaciones
            insertarPublicacion(session, "user123", "post001", "¡Hola mundo!");
            insertarPublicacion(session, "user456", "post002", "Amo la programación");
            insertarPublicacion(session, "user789", "post003", "Neo4j es increíble!");

            // Crear relaciones de seguimiento
            seguirUsuario(session, "user123", "user456");
            seguirUsuario(session, "user456", "user789");

            // Dar "me gusta" a publicaciones
            darLike(session, "user123", "post002");
            darLike(session, "user456", "post003");
            darLike(session, "user789", "post001");

            // Obtener publicaciones populares
            obtenerPublicacionesPopulares(session);

        } catch (Exception e) {
            System.err.println("Error al interactuar con Neo4j: " + e.getMessage());
            e.printStackTrace();  // Imprime el stacktrace completo para diagnóstico.
        }
    }

    private static void insertarUsuario(Session session, String userId) {
        session.writeTransaction(tx -> {
            tx.run("MERGE (u:User {id: $userId})", Values.parameters("userId", userId));
            return null;
        });
        System.out.println("Usuario registrado: " + userId);
    }

    private static void insertarPublicacion(Session session, String userId, String postId, String contenido) {
        session.writeTransaction(tx -> {
            tx.run("MATCH (u:User {id: $userId}) " +
                    "CREATE (p:Post {id: $postId, contenido: $contenido}) " +
                    "MERGE (u)-[:PUBLICÓ]->(p)",
                Values.parameters("userId", userId, "postId", postId, "contenido", contenido));
            return null;
        });
        System.out.println("Publicación creada: " + contenido);
    }

    private static void seguirUsuario(Session session, String userId, String targetId) {
        session.writeTransaction(tx -> {
            tx.run("MATCH (u1:User {id: $userId}), (u2:User {id: $targetId}) " +
                    "MERGE (u1)-[:SIGUE]->(u2)",
                Values.parameters("userId", userId, "targetId", targetId));
            return null;
        });
        System.out.println(userId + " ahora sigue a " + targetId);
    }

    private static void darLike(Session session, String userId, String postId) {
        session.writeTransaction(tx -> {
            tx.run("MATCH (u:User {id: $userId}), (p:Post {id: $postId}) " +
                    "MERGE (u)-[:LIKED]->(p)",
                Values.parameters("userId", userId, "postId", postId));
            return null;
        });
        System.out.println(userId + " dio like a la publicación " + postId);
    }

    private static void obtenerPublicacionesPopulares(Session session) {
        List<Record> posts = session.readTransaction(tx ->
            tx.run("MATCH (p:Post)<-[:LIKED]-(u:User) " +
                    "RETURN p.id AS postId, p.contenido AS contenido, COUNT(u) AS likes " +
                    "ORDER BY likes DESC",
                Values.parameters())
                .list()
        );

        System.out.println("Publicaciones más populares:");
        for (Record record : posts) {
            System.out.println(record.get("contenido").asString() + " (Likes: " + record.get("likes").asInt() + ")");
        }
    }
}
