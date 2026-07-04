async function test() {
  try {
    const res = await fetch('https://aqueduct-gap-clothing.ngrok-free.dev/api/dictionary', {
      method: 'GET',
      headers: {
        'Origin': 'https://example.com',
        'ngrok-skip-browser-warning': 'true'
      }
    });
    console.log('GET /api/dictionary Status:', res.status);
    const text = await res.text();
    console.log('Body:', text.substring(0, 100));
  } catch (err) {
    console.error(err);
  }
}
test();
