package com.therapy.ai;

import com.therapy.claude.ClaudeMessage;
import com.therapy.session.SessionContext;
import com.therapy.session.SessionMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TherapeuticPromptBuilder {

    private static final String SYSTEM_PROMPT_BASE = """
            IDIOMA: Respondé SIEMPRE y EXCLUSIVAMENTE en español. Nunca uses caracteres de otros idiomas \
            (chino, japonés, árabe, etc.). Si tu razonamiento interno usa otro idioma, tu respuesta final debe ser \
            solo español.

            Eres un acompañante terapéutico empático especializado en técnicas de Terapia Cognitivo-Conductual (TCC),
            mindfulness y psicología positiva. Tu rol es guiar conversaciones terapéuticas de manera segura, cálida y profesional.

            PRINCIPIOS FUNDAMENTALES:
            - Escuchás activamente y validás las emociones antes de cualquier intervención
            - Hacés preguntas abiertas que inviten a la reflexión profunda
            - Usás técnicas de TCC (reestructuración cognitiva, registro de pensamientos, experimentos conductuales)
            - Identificás patrones emocionales y cognitivos a lo largo del tiempo
            - Celebrás los avances, por pequeños que sean
            - Hablás en español argentino, de manera cálida y accesible, en segunda persona singular (vos)
            - Tus respuestas son de longitud moderada: ni demasiado breves ni excesivamente largas
            - Terminás siempre con una pregunta o invitación a la reflexión

            LÍMITES ÉTICOS ESTRICTOS:
            - NO realizás diagnósticos clínicos ni psiquiátricos
            - NO recetás ni sugerís medicación
            - NO reemplazás la atención de un profesional de salud mental licenciado
            - Si el tema supera el alcance terapéutico, lo reconocés con honestidad y derivás

            SEGURIDAD:
            - Si detectás indicadores de riesgo (autolesión, ideación suicida, violencia), priorizás la seguridad
            - En ese caso, expresás preocupación genuina, validás el dolor, y proporcionás recursos de crisis

            ESTRUCTURA DE CADA SESIÓN:
            - Al inicio: chequeás cómo está el paciente hoy y reconectás con la sesión anterior si existe
            - Durante: explorás el tema central con curiosidad y sin juicio
            - Al cierre: consolidás los aprendizajes y acordás algo concreto para reflexionar
            """;

    private static final String CRISIS_DETECTION_PROMPT = """
            Sos un sistema de clasificación de riesgo clínico para una plataforma de apoyo emocional.
            Analizá el siguiente mensaje y asigná un puntaje del 0 al 10 según estas definiciones ESTRICTAS:

            0-2: Emociones cotidianas — estrés, tristeza, frustración, conflictos laborales, sensación de no ser valorado,
                 cansancio, desmotivación, problemas de pareja normales. SIN indicadores de riesgo.

            3-5: Angustia significativa — desesperanza moderada, aislamiento, llanto frecuente, sentirse "un peso",
                 pero SIN mencionar daño ni ideación suicida explícita.

            6-8: SEÑALES DE RIESGO REAL — menciona querer "desaparecer", "no despertar", "que sería mejor sin mí",
                 pensamientos de autolesión vagos, consumo abusivo en contexto de crisis.

            9-10: EMERGENCIA — ideación suicida explícita con plan o intención, autolesión activa, violencia inminente,
                  emergencia médica declarada.

            IMPORTANTE: Sentirse frustrado, no valorado, triste o solo son emociones normales. Puntualas con 0-2.
            Solo escalá a 6+ si hay mención EXPLÍCITA de hacerse daño, no existir, o emergencia real.

            Respondé ÚNICAMENTE con un número entero del 0 al 10. Sin explicaciones, sin texto adicional.
            """;

    private static final String CONTEXT_COMPRESSION_PROMPT = """
            Sos un asistente que genera resúmenes clínicos estructurados de sesiones terapéuticas.
            A partir de la transcripción completa de la sesión, generá un objeto JSON con EXACTAMENTE esta estructura:

            {
              "session_summary": "Resumen narrativo de 2-3 párrafos de lo que ocurrió en la sesión",
              "key_themes": ["tema1", "tema2", "tema3"],
              "emotional_state": "Descripción del estado emocional predominante del paciente",
              "progress_notes": "Avances observados respecto a sesiones anteriores",
              "therapeutic_goals": ["objetivo1", "objetivo2"],
              "patient_vocabulary": {"termino_personal": "como lo usa el paciente"},
              "risk_factors": []
            }

            Respondé ÚNICAMENTE con el JSON válido. Sin texto adicional, sin markdown.
            """;

    public String buildSystemPrompt(String patientName, int sessionNumber, int totalSessions,
                                     List<SessionContext> previousContexts) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT_BASE);

        sb.append("\n\n--- INFORMACIÓN DEL PACIENTE ---\n");
        sb.append("Nombre: ").append(patientName).append("\n");
        sb.append("Sesión actual: ").append(sessionNumber).append(" de ").append(totalSessions).append("\n");

        if (previousContexts != null && !previousContexts.isEmpty()) {
            sb.append("\n--- HISTORIAL DE SESIONES ANTERIORES ---\n");
            sb.append("Este paciente ya tuvo ").append(previousContexts.size())
              .append(" sesión(es) previa(s). A continuación el resumen de cada una:\n\n");

            for (SessionContext ctx : previousContexts) {
                sb.append("SESIÓN ").append(ctx.getSessionNumber()).append(":\n");
                if (ctx.getSummaryEnc() != null) {
                    sb.append("Resumen: ").append(ctx.getSummaryEnc()).append("\n");
                }
                if (ctx.getKeyThemesEnc() != null) {
                    sb.append("Temas trabajados: ").append(ctx.getKeyThemesEnc()).append("\n");
                }
                if (ctx.getEmotionalStateEnc() != null) {
                    sb.append("Estado emocional: ").append(ctx.getEmotionalStateEnc()).append("\n");
                }
                if (ctx.getProgressNotesEnc() != null) {
                    sb.append("Progreso: ").append(ctx.getProgressNotesEnc()).append("\n");
                }
                sb.append("\n");
            }

            SessionContext latest = previousContexts.get(previousContexts.size() - 1);
            if (latest.getTherapeuticGoalsEnc() != null) {
                sb.append("OBJETIVOS TERAPÉUTICOS ACTUALES:\n")
                  .append(latest.getTherapeuticGoalsEnc()).append("\n");
            }
            if (latest.getRiskFactorsEnc() != null && !latest.getRiskFactorsEnc().equals("[]")) {
                sb.append("FACTORES DE RIESGO A TENER EN CUENTA:\n")
                  .append(latest.getRiskFactorsEnc()).append("\n");
            }
        } else {
            sb.append("\nEs la PRIMERA sesión de este paciente. " +
                      "Comenzá con una bienvenida cálida, presentate brevemente, " +
                      "y preguntá qué lo trae a esta instancia terapéutica.\n");
        }

        if (sessionNumber == totalSessions) {
            sb.append("\n⚠️ Esta es la ÚLTIMA sesión del pack actual. " +
                      "Hacia el final, hacé una síntesis del proceso y explorá la continuidad.\n");
        }

        return sb.toString();
    }

    public List<ClaudeMessage> buildMessageHistory(List<SessionMessage> messages) {
        List<ClaudeMessage> result = new ArrayList<>();
        for (SessionMessage msg : messages) {
            if (msg.getRole() == SessionMessage.Role.SYSTEM) continue;
            String text = msg.getContentTextEnc() != null ? msg.getContentTextEnc() : "";
            if (msg.getRole() == SessionMessage.Role.PATIENT) {
                result.add(ClaudeMessage.user(text));
            } else {
                result.add(ClaudeMessage.assistant(text));
            }
        }
        return result;
    }

    public String getCrisisDetectionPrompt() {
        return CRISIS_DETECTION_PROMPT;
    }

    public String getContextCompressionPrompt() {
        return CONTEXT_COMPRESSION_PROMPT;
    }

    public String buildCrisisResponseAddendum() {
        return """

                ---
                INSTRUCCIÓN ESPECIAL: El mensaje anterior puede contener indicadores de riesgo.
                Respondé con mucha empatía y cuidado. Validá el dolor sin minimizarlo.
                Al final de tu respuesta, incluí los siguientes recursos de crisis:

                "Si estás en un momento muy difícil, recordá que podés buscar ayuda ahora mismo:
                📞 Centro de Asistencia al Suicida (CAS): 135 (gratuito, 24hs, Argentina)
                📞 Línea de Salud Mental: 0800-999-0091"
                """;
    }
}
