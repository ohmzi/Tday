import { useEffect, useState } from "react";
import { Loader2, ShieldQuestion } from "lucide-react";
import { useAuth } from "@/providers/AuthProvider";
import { api } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/error-message";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  fetchAllSecurityQuestions,
  type SecurityQuestion,
} from "@/lib/securityQuestions";

const SELECT_CLASS =
  "h-10 w-full rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-ring focus:ring-[3px] focus:ring-ring/50";

/**
 * Blocking prompt shown to accounts created before security questions existed.
 * They must choose three questions before continuing so self-service password
 * reset works for them. Backend clears the flag on success.
 */
export default function SetSecurityQuestionsGate() {
  const { user, refreshSession } = useAuth();
  const { toast } = useToast();
  const [questions, setQuestions] = useState<SecurityQuestion[]>([]);
  const [questionIds, setQuestionIds] = useState<[number | null, number | null, number | null]>([
    null,
    null,
    null,
  ]);
  const [answers, setAnswers] = useState<[string, string, string]>(["", "", ""]);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const active = Boolean(user?.requireSecurityQuestions);

  useEffect(() => {
    if (!active || questions.length > 0) return;
    let cancelled = false;
    void fetchAllSecurityQuestions().then((fetched) => {
      if (cancelled) return;
      setQuestions(fetched);
      setQuestionIds([
        fetched[0]?.id ?? null,
        fetched[1]?.id ?? null,
        fetched[2]?.id ?? null,
      ]);
    });
    return () => {
      cancelled = true;
    };
  }, [active, questions.length]);

  if (!active) return null;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    // Validation errors render inline; only the save failure below toasts.
    if (questionIds.some((id) => id == null)) {
      setFormError("Choose three questions");
      return;
    }
    if (new Set(questionIds).size !== 3) {
      setFormError("Choose three different questions");
      return;
    }
    if (answers.some((a) => a.trim().length === 0)) {
      setFormError("Please answer all three questions");
      return;
    }
    setFormError(null);
    setSaving(true);
    try {
      await api.POST({
        url: "/api/user/security-questions",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          answers: questionIds.map((id, i) => ({
            questionId: id as number,
            answer: answers[i].trim(),
          })),
        }),
      });
      await refreshSession();
    } catch (error) {
      toast({
        description: getErrorMessage(error, "Failed to save security questions"),
        variant: "destructive",
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open>
      <DialogContent
        onInteractOutside={(event) => event.preventDefault()}
        onEscapeKeyDown={(event) => event.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldQuestion className="h-5 w-5 text-accent" />
            Set up security questions
          </DialogTitle>
          <DialogDescription>
            Choose three security questions. We'll use them to verify it's you if you
            ever need to reset your password.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-3">
          {[0, 1, 2].map((i) => {
            const otherIds = questionIds.filter((_, idx) => idx !== i);
            const options = questions.filter((q) => !otherIds.includes(q.id));
            return (
              <div key={i} className="space-y-1.5">
                <Label htmlFor={`sq${i + 1}`}>{`Question ${i + 1}`}</Label>
                <select
                  id={`sq${i + 1}`}
                  value={questionIds[i] ?? ""}
                  onChange={(e) => {
                    const next = [...questionIds] as [number | null, number | null, number | null];
                    next[i] = Number(e.target.value);
                    setQuestionIds(next);
                  }}
                  className={SELECT_CLASS}
                >
                  {options.map((q) => (
                    <option key={q.id} value={q.id}>
                      {q.text}
                    </option>
                  ))}
                </select>
                <Input
                  aria-label={`Answer ${i + 1}`}
                  autoComplete="off"
                  placeholder="Answer"
                  value={answers[i]}
                  onChange={(e) => {
                    const next = [...answers] as [string, string, string];
                    next[i] = e.target.value;
                    setAnswers(next);
                  }}
                  required
                />
              </div>
            );
          })}
          {formError ? (
            <p className="text-sm font-medium text-destructive">{formError}</p>
          ) : null}
          <Button type="submit" disabled={saving} className="w-full">
            {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Save security questions
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
