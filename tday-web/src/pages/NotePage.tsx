import { useParams, useNavigate } from "react-router-dom";
import { useEffect } from "react";
import { useNote } from "@/features/notes/query/get-notes";
import Editor from "@/features/notes/component/Editor";
import NoteLoading from "@/components/Sidebar/Note/NoteLoading";
import { DEFAULT_LOCALE } from "@/i18n";

export default function NotePage() {
  const navigate = useNavigate();
  const { id, locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;
  const { notes, notesLoading } = useNote();
  const note = notes.find((n: { id: string }) => n.id === id);

  useEffect(() => {
    if (!notesLoading && !note) {
      navigate(`/${loc}/app/tday`, { replace: true });
    }
  }, [notesLoading, note, navigate, loc]);

  if (notesLoading) return <NoteLoading />;
  if (!note) return null;
  return (
    <div className="pt-6 sm:pt-0">
      <Editor note={note} />
    </div>
  );
}
