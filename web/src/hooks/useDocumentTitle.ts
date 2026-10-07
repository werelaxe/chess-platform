import { useEffect } from "react";

/** Sets the document title; pass the translated text so that a language change updates it. */
export function useDocumentTitle(title: string): void {
  useEffect(() => {
    document.title = title;
  }, [title]);
}
