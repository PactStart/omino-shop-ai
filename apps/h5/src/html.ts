const ALLOWED = new Set(['P', 'BR', 'IMG', 'DIV', 'SPAN', 'STRONG', 'EM', 'B', 'I', 'UL', 'OL', 'LI', 'H1', 'H2', 'H3', 'TABLE', 'TR', 'TD', 'TH'])

export function plainText(html: string): string {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  return (doc.body.textContent || '').replace(/\s+/g, ' ').trim()
}

export function safeHtml(html: string): string {
  const doc = new DOMParser().parseFromString(html, 'text/html')
  const walk = (node: Node) => {
    const children = [...node.childNodes]
    for (const child of children) {
      if (child.nodeType !== Node.ELEMENT_NODE) continue
      const el = child as HTMLElement
      if (!ALLOWED.has(el.tagName)) {
        el.replaceWith(...el.childNodes)
        continue
      }
      for (const attr of [...el.attributes]) {
        const name = attr.name.toLowerCase()
        const keep = (el.tagName === 'IMG' && name === 'src' && /^(https?:|data:image\/)/.test(attr.value)) || name === 'alt'
        if (!keep) el.removeAttribute(attr.name)
      }
      walk(el)
    }
  }
  walk(doc.body)
  return doc.body.innerHTML
}
