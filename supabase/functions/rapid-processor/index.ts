// supabase/functions/generate-meal-images/index.ts
// deno-lint-ignore-file no-explicit-any
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.6';
import OpenAI from 'https://deno.land/x/openai@v4.24.0/mod.ts';
/*  Expected env vars
      SUPABASE_URL
      SUPABASE_SERVICE_ROLE_KEY
      OPENAI_API_KEY
      MAX_IMAGES           (optional per-run default)
*/ /* ───────── helpers ───────── */ const log = (msg, ...args)=>console.log(`${new Date().toISOString()}  ${msg}`, ...args);
const sleep = (ms)=>new Promise((r)=>setTimeout(r, ms));
async function retry(fn, retries = 2, delay = 2_000, tag = 'retry') {
  try {
    return await fn();
  } catch (err) {
    log(`[${tag}] failed (${retries} left)`, err);
    if (retries === 0) throw err;
    await sleep(delay);
    return retry(fn, retries - 1, delay * 2, tag);
  }
}
/* ───────── main handler ───────── */ Deno.serve(async (req)=>{
  log('── invocation ──');
  /* ---- parse & validate ------------------------------------------------- */ const url = new URL(req.url);
  const limit = Number(url.searchParams.get('limit') ?? Deno.env.get('MAX_IMAGES') ?? '10');
  if (!Number.isFinite(limit) || limit <= 0) {
    return new Response('`limit` must be a positive integer', {
      status: 400
    });
  }
  const supabaseUrl = Deno.env.get('SUPABASE_URL');
  const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  const openaiKey = Deno.env.get('OPENAI_API_KEY');
  if (!supabaseUrl || !serviceKey || !openaiKey) {
    log('Missing one or more env vars');
    return new Response('Missing env vars', {
      status: 500
    });
  }
  const supabase = createClient(supabaseUrl, serviceKey);
  /* ---- quick DB read so we can answer caller immediately --------------- */ const selRes = await supabase.from('meals').select('id, full_text, title').is('image_path', null).order('created_at', {
    ascending: false
  }).limit(limit);
  if (selRes.error) {
    log('Select error', selRes.error);
    return new Response('Database error', {
      status: 500
    });
  }
  const meals = selRes.data ?? [];
  const mealIds = meals.map((m)=>m.id);
  /* ---- background processor ------------------------------------------- */ const bgPromise = (async ()=>{
    const openai = new OpenAI({
      apiKey: openaiKey
    });
    const delayPerImage = 500; // ≈2 img requests / second
    const results = [];
    log(`BG: starting, ${meals.length} meals`);
    for (const meal of meals){
      log(`BG: meal ${meal.id} …`);
      try {
        /* generate prompt & image */ const cleaned = meal.full_text.replace(/\s*\([^)]*\)/g, '').trim();
        const prompt = `High-quality food photo on a white background. The main food should be on a plate and the sides in small bowls. Everything should be on a white plastic tray. No Text. ${cleaned}`;
        const imgResp = await retry(()=>openai.images.generate({
            model: 'gpt-image-1',
            prompt,
            n: 1,
            size: '1024x1024',
            quality: 'low'
          }), 2, 2_000, `openai-${meal.id}`);
        if (!imgResp.data?.length || !imgResp.data[0].b64_json) {
          throw new Error('No image returned');
        }
        const b64 = imgResp.data[0].b64_json;
        const binStr = atob(b64);
        const imgBuf = new Uint8Array(binStr.length);
        for(let i = 0; i < binStr.length; i++)imgBuf[i] = binStr.charCodeAt(i);
        /* upload to Storage */ const path = `${meal.id}.png`;
        const upRes = await supabase.storage.from('mensa-food').upload(path, imgBuf, {
          contentType: 'image/png',
          upsert: true
        });
        if (upRes.error) throw upRes.error;
        /* update row */ const updRes = await supabase.from('meals').update({
          image_path: path
        }).eq('id', meal.id).single();
        if (updRes.error) throw updRes.error;
        const updGeneric = await supabase.from('meals').update({
          image_path_generic: path
        }).eq('title', meal.title);
        if (updGeneric.error) throw updGeneric.error;
        log(`BG: ✔ meal ${meal.id}`);
        results.push({
          id: meal.id,
          status: 'ok',
          path
        });
      } catch (err) {
        log(`BG: ✖ meal ${meal.id}`, err);
        results.push({
          id: meal.id,
          status: 'error',
          message: `${err}`
        });
      }
      await sleep(delayPerImage);
    }
    log(`BG: finished run (${results.length} processed)`);
  })();
  /* ---- keep the runtime alive for bgPromise, but answer now ------------ */ // EdgeRuntime.waitUntil keeps the function running *after* we return.
  // Doc: https://supabase.com/docs/guides/functions/background-tasks :contentReference[oaicite:0]{index=0}
  //      https://supabase.com/blog/edge-functions-background-tasks :contentReference[oaicite:1]{index=1}
  EdgeRuntime?.waitUntil?.(bgPromise);
  return new Response(JSON.stringify({
    accepted: true,
    processing: mealIds
  }), {
    status: 202,
    headers: {
      'Content-Type': 'application/json'
    }
  });
});
