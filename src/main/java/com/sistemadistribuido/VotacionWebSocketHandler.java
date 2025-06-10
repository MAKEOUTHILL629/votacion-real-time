package com.sistemadistribuido;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import java.io.IOException;

@WebSocket
public class VotacionWebSocketHandler {

    // Se llama cuando un cliente (navegador) se conecta
    @OnWebSocketConnect
    public void onConnect(Session userSession) throws IOException {
        System.out.println("Nuevo usuario conectado: " + userSession.getRemoteAddress());
        // Agregamos la sesión del usuario a nuestra lista de sesiones activas
        VotacionApp.addSession(userSession);
        // Le enviamos inmediatamente el estado actual de los votos
        VotacionApp.sendCurrentVotesToUser(userSession);
    }

    // Se llama cuando un cliente se desconecta
    @OnWebSocketClose
    public void onClose(Session userSession, int statusCode, String reason) {
        System.out.println("Usuario desconectado: " + userSession.getRemoteAddress());
        // Removemos la sesión del usuario de la lista
        VotacionApp.removeSession(userSession);
    }

    // Se llama cuando el servidor recibe un mensaje del cliente.
    // Para este proyecto no lo usaremos, ya que los votos llegan por HTTP.
    @OnWebSocketMessage
    public void onMessage(Session userSession, String message) {
        System.out.println("Mensaje recibido de " + userSession.getRemoteAddress() + ": " + message);
    }
}