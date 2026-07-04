async function testSearch() {
  const keyword = encodeURIComponent('hello');

  const res = await fetch(
    `https://ai-dictionary-backend-36vo.onrender.com/api/dictionary/search?keyword=${keyword}`,
    {
      method: 'GET'
    }
  );

  console.log('Search Status:', res.status);
  console.log(await res.text());
}

testSearch();
