import { useState } from "react";

/** Product photo from the store's CDN; a neutral placeholder when offline or missing. */
export function Thumb({ src }: { src: string | null }) {
  const [failed, setFailed] = useState(false);
  if (!src || failed) return <div className="thumb" aria-hidden="true" />;
  return (
    <img
      className="thumb"
      src={src}
      alt=""
      loading="lazy"
      onError={() => setFailed(true)}
    />
  );
}
