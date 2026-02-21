"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import { signIn } from "next-auth/react";
import { Mail, Lock, Loader2 } from "lucide-react";
import { Link, useRouter } from "@/i18n/navigation";
import { useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import PendingApprovalDialog from "@/components/auth/PendingApprovalDialog";

export default function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [pendingApprovalOpen, setPendingApprovalOpen] = useState(false);

  useEffect(() => {
    if (searchParams.get("pending") === "1") {
      setPendingApprovalOpen(true);
    }
  }, [searchParams]);

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setErrorMessage("");
    setIsSubmitting(true);

    try {
      const result = await signIn("credentials", {
        email,
        password,
        redirect: false,
      });

      if (result?.error) {
        if (result.code === "pending_approval") {
          setPendingApprovalOpen(true);
          return;
        }
        setErrorMessage("Invalid email or password.");
        return;
      }

      router.replace("/app/tday");
      router.refresh();
    } catch (error) {
      console.error(error);
      setErrorMessage("Unable to sign in. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <>
      <Card className="border-0 bg-card/80 shadow-xl backdrop-blur-sm">
        <CardHeader className="space-y-4 pb-2 text-center">
        <div className="mx-auto flex items-center justify-center">
          <Image src="/tday-icon.svg" alt="T'Day" width={64} height={64} />
        </div>
        <div className="space-y-1">
          <CardTitle className="text-3xl font-serif">Welcome Back</CardTitle>
          <CardDescription className="text-muted-foreground">
            {"Sign in to continue to T'Day"}
          </CardDescription>
        </div>
      </CardHeader>
        <CardContent className="pt-4">
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="email"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => {
                  setEmail(e.target.value);
                  if (errorMessage) {
                    setErrorMessage("");
                  }
                }}
                className="h-12 bg-background/50 pl-10"
                required
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">Password</Label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="password"
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value);
                  if (errorMessage) {
                    setErrorMessage("");
                  }
                }}
                className="h-12 bg-background/50 pl-10"
                required
              />
            </div>
          </div>

          {errorMessage && (
            <p className="text-center text-sm text-destructive">{errorMessage}</p>
          )}

          <Button
            type="submit"
            className="h-12 w-full font-medium text-primary-foreground"
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Signing in...
              </>
            ) : (
              "Sign In"
            )}
          </Button>
        </form>

        <div className="mt-6 text-center">
          <p className="text-sm text-muted-foreground">
            Don&apos;t have an account?{" "}
            <Link
              href="/register"
              className="font-medium text-accent transition-colors hover:text-accent/80"
            >
              Create one
            </Link>
          </p>
        </div>
        </CardContent>
      </Card>
      <PendingApprovalDialog
        open={pendingApprovalOpen}
        onOpenChange={setPendingApprovalOpen}
      />
    </>
  );
}
