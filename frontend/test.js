async function test() {
  try {
    const res = await fetch(
      'https://ai-dictionary-backend-36vo.onrender.com/api/dictionary/health',
      {
        method: 'GET'
      }
    );

    console.log('GET /api/dictionary/health Status:', res.status);

    const text = await res.text();
    console.log('Body:', text.substring(0, 200));
  } catch (err) {
    console.error('Test API error:', err);
  }
}

test();
