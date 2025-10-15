export async function loadPartial(path) {
  const res = await fetch(path);
  if (!res.ok) throw new Error('Failed to load ' + path);
  const html = await res.text();
  document.getElementById('app').innerHTML = html;
}
