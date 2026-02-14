"use client";
import { useParams, useRouter } from "next/navigation";
import React, { useEffect } from "react";
import { useNote } from "@/features/notes/query/get-notes";
import Editor from "@/features/notes/component/Editor";
import NoteLoading from "@/components/Sidebar/Note/NoteLoading";
const Page = () => {
  const router = useRouter();
  const params = useParams();
  const { notes, notesLoading } = useNote();
  const note = notes.find(({ id }) => id === params.id);

  useEffect(() => {
    if (!notesLoading && !note) {
      router.replace("/app/tday");
    }
  }, [notesLoading, note, router]);

  if (notesLoading) return <NoteLoading />;

  if (!note) return null;

  return <div className="pt-6 sm:pt-0"><Editor note={note} /></div>;
};

export default Page;
