import express from "express";
import path from "path";
import { createServer as createViteServer } from "vite";
import { GoogleGenAI } from "@google/genai";
import dotenv from "dotenv";

dotenv.config();

async function startServer() {
  const app = express();
  const PORT = 3000;

  // Increase payload size limit for base64 images
  app.use(express.json({ limit: "50mb" }));

  // Use the Render URL explicitly
  const SPRING_BOOT_URL = "https://ai-dictionary-backend-36vo.onrender.com";
  
  app.use("/api/dictionary", async (req, res) => {
    try {
      const targetUrl = `${SPRING_BOOT_URL}/api/dictionary${req.url}`;
      const headers = new Headers();
      for (const [key, value] of Object.entries(req.headers)) {
        if (key.toLowerCase() !== 'connection' && key.toLowerCase() !== 'content-length' && value !== undefined) {
          if (Array.isArray(value)) {
            value.forEach(v => headers.append(key, v));
          } else {
            headers.set(key, value);
          }
        }
      }
      headers.set('host', new URL(SPRING_BOOT_URL).host);

      const options: RequestInit = {
        method: req.method,
        headers,
      };

      if (['POST', 'PUT', 'PATCH'].includes(req.method)) {
        options.body = JSON.stringify(req.body);
      }

      const response = await fetch(targetUrl, options);
      const text = await response.text();
      
      const contentType = response.headers.get('content-type');
      if (contentType) {
        res.setHeader('Content-Type', contentType);
      }
      
      res.status(response.status).send(text);
    } catch (error) {
      console.error("Proxy error:", error);
      res.status(500).json({ error: "Proxy error: " + error.message });
    }
  });

  // API Routes
  app.get("/api/health", (req, res) => {
    res.json({ status: "ok" });
  });

  app.post("/api/vision", async (req, res) => {
    try {
      const { image, prompt } = req.body;
      if (!image) {
        return res.status(400).json({ error: "No image provided" });
      }

      if (!process.env.GEMINI_API_KEY) {
        return res.status(500).json({ error: "GEMINI_API_KEY is not configured on the server" });
      }

      const ai = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY });
      
      // Handle the base64 string (remove data URI prefix if present)
      let base64Data = image;
      let mimeType = "image/jpeg";
      
      if (image.startsWith("data:")) {
        const matches = image.match(/^data:([a-zA-Z0-9]+\/[a-zA-Z0-9-.+]+);base64,(.+)$/);
        if (matches && matches.length === 3) {
          mimeType = matches[1];
          base64Data = matches[2];
        }
      }

      const response = await ai.models.generateContent({
        model: "gemini-2.5-flash",
        contents: [
          {
            role: "user",
            parts: [
              {
                inlineData: {
                  data: base64Data,
                  mimeType: mimeType
                }
              },
              {
                text: prompt || "Please transcribe any text in this image. Only output the text, no other comments."
              }
            ]
          }
        ]
      });

      res.json({ text: response.text });
    } catch (error) {
      console.error("Vision API Error:", error);
      res.status(500).json({ error: error.message || "Failed to process image" });
    }
  });

  // Vite middleware for development
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
