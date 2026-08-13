# LLM Engine Benchmark — Linux/Docker

Proyecto Maven para ejecutar y comparar tres servidores de inferencia:

- JLama 0.8.4: `http://localhost:8080/chat/completions`
- Ollama: `http://localhost:11434/api/generate`
- vLLM: `http://localhost:8000/v1/chat/completions`

## Requisitos

- Linux con Bash.
- JDK 25, Maven y Docker Compose.
- NVIDIA Container Toolkit para Ollama y vLLM.
- Token de Hugging Face para vLLM, porque el modelo de Meta requiere acceso.

## Inicio rápido

Desde la raíz del proyecto:

```bash
export HF_TOKEN='hf_TU_TOKEN'
bash llm-engine-benchmark/Comandos.txt
```

El archivo `Comandos.txt` compila el benchmark, construye la imagen oficial de
JLama 0.8.4, inicia cada servidor por separado y guarda las mediciones en
`bench-logs/`.

También puedes iniciar un servicio manualmente:

```bash
docker compose up -d jlama
docker compose logs -f jlama
```

## Modelo y comparación

Se utilizan estas referencias equivalentes de Llama 3.2 1B:

- JLama: `tjake/Llama-3.2-1B-Instruct-JQ4`.
- Ollama: `llama3.2:1b-Instruct-fp16`.
- vLLM: `meta-llama/Llama-3.2-1B-Instruct`.

Los formatos y cuantizaciones no son idénticos: JLama utiliza JQ4, Ollama su
formato empaquetado y vLLM SafeTensors. Esa diferencia debe quedar registrada
al interpretar los resultados.

El benchmark fija el máximo de generación en 128 tokens, temperatura 0 y
contexto objetivo 2048 para las pruebas del flujo Linux. vLLM se configura con
contexto máximo 2048 para mantenerse dentro de la VRAM disponible; Ollama recibe
el mismo límite mediante la opción `num_ctx` del runner. JLama 0.8.4 toma el
contexto desde la configuración del modelo y no expone un parámetro equivalente
en su comando `restapi`; por ello esa limitación debe anotarse en el informe y
no presentarse como igualdad estricta de contexto.

## Limpieza

```bash
docker compose down --remove-orphans
```

Los modelos, logs, credenciales y directorios `target/` están excluidos del
ZIP y del control de versiones.
