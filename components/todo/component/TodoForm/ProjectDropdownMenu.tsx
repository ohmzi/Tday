import { Input } from '@/components/ui/input';
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover';
import { ChevronDown, Plus, Trash } from 'lucide-react';
import React, { useState, useMemo, SetStateAction } from 'react';
import { Button } from '@/components/ui/button';
import { useProjectMetaData } from '@/components/Sidebar/Project/query/get-project-meta';
import { DropdownMenuSeparator } from '@/components/ui/dropdown-menu';
import ProjectTag from '@/components/ProjectTag';
import { cn } from '@/lib/utils';
import { useCreateProject } from '@/components/Sidebar/Project/query/create-project';

type ProjectDropdownMenuProp = {
    projectID: string | null;
    setProjectID: React.Dispatch<SetStateAction<string | null>>;
    className?: string;
    variant?: "default" | "noHash"
}

export default function ProjectDropdownMenu({ projectID, setProjectID, className, variant = "default" }: ProjectDropdownMenuProp) {
    const { projectMetaData } = useProjectMetaData();
    const { createMutateAsync, createLoading } = useCreateProject();
    const [open, setOpen] = useState(false);
    const [search, setSearch] = useState('');
    const normalizedSearch = search.replace(/^#+\s*/, "").trim();

    // Filter tags based on search input
    const filteredProjects = useMemo(() => {
        if (!search.trim()) return Object.entries(projectMetaData);
        const lowerSearch = search.toLowerCase();
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        return Object.entries(projectMetaData).filter(([_, value]) =>
            value.name.toLowerCase().includes(lowerSearch)
        );
    }, [search, projectMetaData]);

    const hasExactMatch = useMemo(() => {
        if (!normalizedSearch) return false;
        return Object.values(projectMetaData).some((value) =>
            value.name.replace(/^#+\s*/, "").trim().toLowerCase() === normalizedSearch.toLowerCase()
        );
    }, [normalizedSearch, projectMetaData]);

    const canCreateTag = normalizedSearch.length > 0 && !hasExactMatch;

    return (
        <Popover modal={true} open={open} onOpenChange={setOpen}>
            <PopoverTrigger asChild>
                <Button variant="ghost" type="button" className={cn("h-fit px-2! gap-1 text-muted-foreground font-normal shrink-0", className)}>
                    {projectID
                        ?
                        <>
                            <ProjectTag id={projectID} className='text-sm pr-0' /> <span className='truncate max-w-14 sm:max-w-24 md:max-w-52 lg:max-w-none'>{projectMetaData[projectID]?.name?.replace(/^#+\s*/, "")}</span>
                        </>
                        : <>
                            {variant == "default" && <span>#</span>}<p>Tag</p>
                        </>
                    }
                    <ChevronDown className="w-4 h-4 text-muted-foreground" />
                </Button>
            </PopoverTrigger>

            <PopoverContent className="p-1 space-y-1 text-sm">
                <Input
                    placeholder="Search tags..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    className="text-[1.1rem]! md:text-base! lg:text-sm! w-full mb-1 bg-inherit brightness-75  outline-0 rounded-sm ring-0 ring-black focus-visible:ring-0 focus-visible:ring-offset-0"
                    onKeyDown={(e) => e.stopPropagation()}
                    autoFocus
                />
                {canCreateTag && (
                    <button
                        type="button"
                        onClick={async () => {
                            try {
                                const created = await createMutateAsync({ name: normalizedSearch });
                                if (created?.id) {
                                    setProjectID(created.id);
                                    setOpen(false);
                                    setSearch("");
                                }
                            } catch {
                                // handled in hook via toast
                            }
                        }}
                        disabled={createLoading}
                        className="flex w-full items-center gap-2 rounded-sm p-1.5 text-left text-sm hover:bg-popover-accent disabled:opacity-60"
                    >
                        <Plus className="h-4 w-4" />
                        {createLoading ? "Creating..." : `Create #${normalizedSearch}`}
                    </button>
                )}
                {filteredProjects.length === 0 && !canCreateTag && (
                    <p className='text-sm text-muted-foreground py-10 text-center w-full'>
                        No tags...
                    </p>
                )}
                {filteredProjects.map(([key, value]) => (
                    <div
                        key={key}
                        className='text-sm cursor-pointer p-1.5 rounded-sm hover:bg-popover-accent'
                        onClick={() => {
                            setProjectID(key);
                            setOpen(false);
                        }}
                    >
                        <ProjectTag id={key} className='text-sm pr-0' /> {value.name.replace(/^#+\s*/, "")}
                    </div>
                ))}

                {projectID &&
                    <>
                        <DropdownMenuSeparator />
                        <div
                            className='flex gap-2 cursor-pointer p-1.5 rounded-sm hover:bg-red/80 hover:text-white'
                            onClick={() => {
                                setProjectID(null);
                                setOpen(false);
                            }}
                        >
                            <Trash strokeWidth={1.7} className='w-4 h-4' />
                            Clear
                        </div>
                    </>
                }
            </PopoverContent>
        </Popover>
    );
}
