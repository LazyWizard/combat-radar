package org.lazywizard.radar;

import java.util.Objects;

class RendererWrapper<T> implements Comparable<RendererWrapper>
{
    private final Class<T> renderClass;
    private final int renderOrder;

    RendererWrapper(Class<T> renderClass, int renderOrder)
    {
        this.renderClass = renderClass;
        this.renderOrder = renderOrder;
    }

    Class<T> getRendererClass()
    {
        return renderClass;
    }

    int getRenderOrder()
    {
        return renderOrder;
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == null)
        {
            return false;
        }

        if (!(other instanceof RendererWrapper))
        {
            return false;
        }

        RendererWrapper tmp = (RendererWrapper) other;
        return renderClass.equals(tmp.renderClass) && renderOrder == tmp.renderOrder;
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 61 * hash + Objects.hashCode(this.renderClass);
        hash = 61 * hash + this.renderOrder;
        return hash;
    }

    @Override
    public int compareTo(RendererWrapper other)
    {
        return Integer.compare(this.renderOrder, other.renderOrder);
    }
}
