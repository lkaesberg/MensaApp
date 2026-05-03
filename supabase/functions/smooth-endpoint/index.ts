// supabase/functions/generate-meal-images/index.ts
// deno-lint-ignore-file no-explicit-any
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.39.6';
import OpenAI from 'https://deno.land/x/openai@v4.24.0/mod.ts';
// Import ImageScript for compression/resizing
import { Image } from 'https://deno.land/x/imagescript@1.2.15/mod.ts';
/* Expected env vars
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
  const limit = Number(url.searchParams.get('limit') ?? Deno.env.get('MAX_IMAGES') ?? '8');
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
  /* ---- fetch all rows without generic image ----------------------------- */ const selRes = await supabase.from('meals').select('id, title, full_text').is('image_path_generic', null);
  if (selRes.error) {
    log('Select error', selRes.error);
    return new Response('Database error', {
      status: 500
    });
  }
  const meals = selRes.data ?? [];
  // group by title
  const grouped = {};
  for (const m of meals){
    if (!grouped[m.title]) grouped[m.title] = [];
    grouped[m.title].push(m);
  }
  // sort titles by group size desc and take top N
  const sortedTitles = Object.entries(grouped).sort((a, b)=>b[1].length - a[1].length).slice(0, limit).map(([title, list])=>({
      title,
      meals: list
    }));
  const processingTitles = sortedTitles.map((t)=>t.title);
  /* ---- background processor --------------------------------------------- */ const bgPromise = (async ()=>{
    const openai = new OpenAI({
      apiKey: openaiKey
    });
    const delayPerImage = 500; // ≈2 img requests / second
    const results = [];
    log(`BG: starting, ${sortedTitles.length} titles`);
    for (const entry of sortedTitles){
      const title = entry.title;
      log(`BG: title "${title}" …`);
      try {
        // use first meal's full_text as representative
        const meal = entry.meals[0];
        const cleaned = meal.full_text.replace(/\s*\([^)]*\)/g, '').trim();
        const prompt = `High-quality food photo on a white background. ` + `The main food should be on a plate and the sides in small bowls. ` + `Everything should be on a white plastic tray. No Text: ${cleaned}`;
        // generate image
        const imgResp = await retry(()=>openai.images.generate({
            model: 'gpt-image-1',
            prompt,
            n: 1,
            size: '1024x1024',
            quality: 'low'
          }), 2, 2_000, `openai-${title}`);
        if (!imgResp.data?.length || !imgResp.data[0].b64_json) {
          throw new Error('No image returned');
        }
        const b64 = imgResp.data[0].b64_json;
        const binStr = atob(b64);
        const imgBuf = new Uint8Array(binStr.length);
        for(let i = 0; i < binStr.length; i++){
          imgBuf[i] = binStr.charCodeAt(i);
        }
        /* ──── COMPRESSION START ──── */ // 1. Decode PNG
        const image = await Image.decode(imgBuf);
        // 2. Resize (Optional: 1024 is usually too big for UI cards. 512 is plenty crisp)
        // If you strictly want 1024, remove this line.
        image.resize(512, 512);
        // 3. Encode to JPEG at 75% quality (Massive size reduction vs PNG)
        const compressedBuf = await image.encodeJPEG(75);
        /* ──── COMPRESSION END ──── */ // upload to storage (Note: changed extension to .jpg)
        const safeTitle = title.replace(/\W+/g, '_').toLowerCase();
        const path = `generic/${safeTitle}.jpg`;
        const upRes = await supabase.storage.from('mensa-food').upload(path, compressedBuf, {
          contentType: 'image/jpeg',
          upsert: true
        });
        if (upRes.error) throw upRes.error;
        // update all rows for this title
        const updRes = await supabase.from('meals').update({
          image_path_generic: path
        }).eq('title', title);
        if (updRes.error) throw updRes.error;
        log(`BG: ✔ title "${title}" (Size: ${(compressedBuf.length / 1024).toFixed(1)}kb)`);
        results.push({
          title,
          status: 'ok',
          path
        });
      } catch (err) {
        log(`BG: ✖ title "${title}"`, err);
        results.push({
          title,
          status: 'error',
          message: `${err}`
        });
      }
      await sleep(delayPerImage);
    }
    log(`BG: finished run (${results.length} processed)`);
  })();
  /* ---- respond immediately ---------------------------------------------- */ // @ts-ignore: EdgeRuntime is available in Supabase
  EdgeRuntime?.waitUntil?.(bgPromise);
  return new Response(JSON.stringify({
    accepted: true,
    processing: processingTitles
  }), {
    status: 202,
    headers: {
      'Content-Type': 'application/json'
    }
  });
});
