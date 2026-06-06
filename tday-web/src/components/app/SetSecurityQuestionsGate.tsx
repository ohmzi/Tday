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

/**
 * Blocking prompt shown to accounts created before security questions existed.
 * They must choose two questions before continuing so self-service password
 * reset works for them. Backend clears the flag on success.
 */
export default function SetSecurityQuestionsGate() {
  const { user, refreshSession } = useAuth();
  const { toast } = useToast();
  const [questions, setQuestions] = useState<SecurityQuestion[]>([]);
  const [questionId1, setQuestionId1] = useState<number | null>(null);
  const [answer1, setAnswer1] = useState("");
  const [questionId2, setQuestionId2] = useState<number | null>(null);
  const [answer2, setAnswer2] = useState("");
  const [saving, setSaving] = useState(false);

  const active = Boolean(user?.requireSecurityQuestions);

  useEffect(() => {
    if (!active || questions.length > 0) return;
    let cancelled = false;
    void fetchAllSecurityQuestions().then((fetched) => {
      if (cancelled) return;
      setQuestions(fetched);
      if (fetched[0]) setQuestionId1(fetched[0].id);
      if (fetched[1]) setQuestionId2(fetched[1].id);
    });
    return () => {
      cancelled = true;
    };
  }, [active, questions.length]);

  if (!active) return null;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (questionId1 == null || questionId2 == null || questionId1 === questionId2) {
      toast({ description: "Choose two different questions", variant: "destructive" });
      return;
    }
    if (answer1.trim().length === 0 || answer2.trim().length === 0) {
      toast({ description: "Please answer both questions", variant: "destructive" });
      return;
    }
    setSaving(true);
    try {
      await api.POST({
        url: "/api/user/security-questions",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          answers: [
            { questionId: questionId1, answer: answer1.trim() },
            { questionId: questionId2, answer: answer2.trim() },
          ],
        }),
      });
      toast({ description: "Security questions saved" });
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
            Choose two security questions. We'll use them to verify it's you if you
            ever need to reset your password.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="sq1">Question 1</Label>
            <select
              id="sq1"
              value={questionId1 ?? ""}
              onChange={(e) => setQuestionId1(Number(e.target.value))}
              className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-ring focus:ring-[3px] focus:ring-ring/50"
            >
              {questions
                .filter((q) => q.id !== questionId2)
                .map((q) => (
                  <option key={q.id} value={q.id}>
                    {q.text}
                  </option>
                ))}
            </select>
            <Input
              aria-label="Answer 1"
              autoComplete="off"
              placeholder="Answer"
              value={answer1}
              onChange={(e) => setAnswer1(e.target.value)}
              required
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="sq2">Question 2</Label>
            <select
              id="sq2"
              value={questionId2 ?? ""}
              onChange={(e) => setQuestionId2(Number(e.target.value))}
              className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm outline-none focus:border-ring focus:ring-[3px] focus:ring-ring/50"
            >
              {questions
                .filter((q) => q.id !== questionId1)
                .map((q) => (
                  <option key={q.id} value={q.id}>
                    {q.text}
                  </option>
                ))}
            </select>
            <Input
              aria-label="Answer 2"
              autoComplete="off"
              placeholder="Answer"
              value={answer2}
              onChange={(e) => setAnswer2(e.target.value)}
              required
            />
          </div>
          <Button type="submit" disabled={saving} className="w-full">
            {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Save security questions
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
