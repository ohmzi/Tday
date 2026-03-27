import { changeMapType } from "@/features/todayTodos/query/reorder-todo";
import { TodoItemTypeWithDateChecksum } from "@/features/todayTodos/query/update-todo";
import { TodoItemType } from "@/types";
import { QueryStatus, UseMutateFunction } from "@tanstack/react-query";
import React, { createContext, useContext } from "react";

// hook types
type UseDeleteTodoType = () => {
    deleteMutateFn: (variables: { id: string }) => void;
    deletePending: boolean;
}
type UseCompleteTodoType = () => {
    completeMutateFn: UseMutateFunction<void, Error, TodoItemType, { oldTodos: TodoItemType[]; }>;
    completePending: boolean
}
type UsePinTodoType = () => {
    pinMutateFn: UseMutateFunction<void, Error, TodoItemType, { oldTodos: unknown; }>;
    pinPending: boolean
}
type UsePrioritizeTodoType = () => {
    prioritizeMutateFn: UseMutateFunction<void, Error, { id: string; level: "Low" | "Medium" | "High"; isRecurring: boolean; }, { oldTodos: TodoItemType[] | undefined; }>;
    prioritizePending: boolean
}

type UseReorderTodoType = () => {
    reorderMutateFn: UseMutateFunction<void, Error, changeMapType[], unknown>;
    reorderPending: boolean
}

type UseEditTodoType = () => {
    editTodoMutateFn: UseMutateFunction<void, Error, TodoItemTypeWithDateChecksum, { oldTodos: TodoItemType[] | undefined; }>
    editTodoStatus: QueryStatus | "idle"
}

type UseEditTodoInstanceType = (setEditInstanceOnly: React.Dispatch<React.SetStateAction<boolean>> | undefined) => {
    editTodoInstanceMutateFn: UseMutateFunction<void, Error, TodoItemType, { oldTodos: TodoItemType[] | undefined; }>
    editTodoInstanceStatus: QueryStatus | "idle"
}

type TodoMutaionProviderProps = {
    useDeleteTodo: UseDeleteTodoType,
    useCompleteTodo: UseCompleteTodoType,
    usePinTodo: UsePinTodoType,
    usePrioritizeTodo: UsePrioritizeTodoType,
    useReorderTodo: UseReorderTodoType,
    useEditTodoInstance: UseEditTodoInstanceType,
    useEditTodo: UseEditTodoType,
    children: React.ReactNode
}

const TodoMutationContext = createContext<Omit<TodoMutaionProviderProps, "children"> | null>(null)


export default function TodoMutationProvider({ useDeleteTodo, useCompleteTodo, usePinTodo, usePrioritizeTodo, useReorderTodo, useEditTodo, useEditTodoInstance, children }: TodoMutaionProviderProps) {
    return (
        <TodoMutationContext.Provider value={{ useDeleteTodo, useCompleteTodo, usePinTodo, usePrioritizeTodo, useReorderTodo, useEditTodo, useEditTodoInstance }}>
            {children}
        </TodoMutationContext.Provider>
    )
}

export function useTodoMutation() {
    const context = useContext(TodoMutationContext)
    if (!context) throw new Error('useTodoMutation must be used within TodoMutationProvider');
    return context
}
