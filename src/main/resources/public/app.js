// Se ejecuta cuando toda la página se ha cargado
window.onload = function() {
    conectarWebSocket();
};

const statusSpan = document.getElementById('status');
const votosOpcionA = document.getElementById('votos-Opcion A');
const votosOpcionB = document.getElementById('votos-Opcion B');
let websocket;

function conectarWebSocket() {
    // Construye la URL del WebSocket. Si la página es http, el ws es ws://. Si es https, es wss://.
    const wsScheme = window.location.protocol === "https:" ? "wss" : "ws";
    const wsURL = wsScheme + "://" + window.location.host + "/votacion";

    console.log("Conectando a: " + wsURL);
    statusSpan.textContent = "Conectando...";
    statusSpan.style.color = "orange";

    websocket = new WebSocket(wsURL);

    // Evento que se dispara cuando la conexión se establece con éxito
    websocket.onopen = function(event) {
        console.log("Conexión WebSocket establecida.");
        statusSpan.textContent = "Conectado";
        statusSpan.style.color = "green";
    };

    // Evento que se dispara cada vez que se recibe un mensaje del servidor
    websocket.onmessage = function(event) {
        console.log("Mensaje recibido del servidor: ", event.data);
        // El servidor envía el estado completo de los votos en formato JSON
        const votos = JSON.parse(event.data);

        // Actualizamos los contadores en la página HTML
        votosOpcionA.textContent = votos["Opcion A"] || 0;
        votosOpcionB.textContent = votos["Opcion B"] || 0;
    };

    // Evento que se dispara si la conexión se cierra
    websocket.onclose = function(event) {
        console.log("Conexión WebSocket cerrada. Intentando reconectar en 5 segundos...");
        statusSpan.textContent = "Desconectado";
        statusSpan.style.color = "red";
        // Intenta reconectar después de 5 segundos
        setTimeout(conectarWebSocket, 5000);
    };

    // Evento para manejar cualquier error en la conexión
    websocket.onerror = function(event) {
        console.error("Error en WebSocket: ", event);
        statusSpan.textContent = "Error de Conexión";
        statusSpan.style.color = "red";
    };
}

// Función que se llama al hacer clic en los botones de votar
function votar(opcion) {
    console.log("Enviando voto para: " + opcion);
    // Usamos la API Fetch para enviar una petición POST al servidor
    fetch('/vote', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: 'option=' + encodeURIComponent(opcion)
    })
        .then(response => {
            if (!response.ok) {
                console.error("Error al enviar el voto.");
            }
        })
        .catch(error => console.error('Error:', error));
}