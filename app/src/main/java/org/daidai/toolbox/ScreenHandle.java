package org.daidai.toolbox;

import java.lang.reflect.*;

/** Compatibility bridge for the first host ABI. Optional lifecycle hooks are no-ops. */
public final class ScreenHandle {
    private final Object target;
    public ScreenHandle(Object target){this.target=target;}
    Object sessionTarget(){return target;}
    public Object call(String name,Object...args){
        for(Method m:target.getClass().getMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length){
            try{return m.invoke(target,args);}catch(InvocationTargetException e){Throwable cause=e.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;throw new IllegalStateException(cause);}catch(Exception e){throw new IllegalStateException(e);}
        }
        return null;
    }
}
